package org.powerassert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

import com.sun.source.tree.AssertTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

class JavacPowerAssertGenerator extends TreePathScanner<TreePath, Context> implements PowerAssertGenerator {
	private TreeMaker treeMaker;
	private JavacElements elements;
	private CharSequence rawSource;

	private Trees trees;
	private Context context;
	private Messager messager;

	@Override
	public void init(ProcessingEnvironment env) {
		this.trees = Trees.instance(env);
		this.context = ((JavacProcessingEnvironment) env).getContext();
		this.messager = env.getMessager();
	}

	@Override
	public boolean scan(Element element) {
		try {
			this.rawSource = ((Symbol.ClassSymbol) element).sourcefile.getCharContent(true);
			TreePath path = trees.getPath(element);

			treeMaker = TreeMaker.instance(context);
			elements = JavacElements.instance(context);

			scan(path, context);

			return true;
		} catch(IOException e) {
			messager.printMessage(Diagnostic.Kind.WARNING,
					"Unable to find raw source for element " + element.getSimpleName().toString());
		} catch(NoClassDefFoundError ignored) {
			messager.printMessage(Diagnostic.Kind.WARNING,
					"Unable to generate power assertions because javac is not compiling the code");
		}
		return false;
	}

	private static java.util.List<String> junitAsserts = Arrays.asList("assertEquals", "assertNotEquals",
			"assertArrayEquals", "assertTrue", "assertFalse", "assertSame", "assertNotSame", "assertNull",
			"assertNotNull");

	@Override
	public TreePath visitMethodInvocation(MethodInvocationTree node, Context context) {
		JCTree.JCMethodInvocation meth = (JCTree.JCMethodInvocation) node;
		Tree parent = getCurrentPath().getParentPath().getLeaf();

		if(parent instanceof JCTree.JCStatement) {
			JCTree.JCStatement statement = (JCTree.JCStatement) parent;
			String methodName = methodName(meth);

			if(junitAsserts.contains(methodName)) {
				JCTree.JCExpression recorded = treeMaker.Apply(
						List.<JCTree.JCExpression>nil(),
						qualifiedName("org", "powerassert", "synthetic", "junit", "Assert", methodName),
						recordEach(meth.getArguments())
				);

				JCTree.JCExpressionStatement instrumented = treeMaker.Exec(
						treeMaker.Apply(
								List.<JCTree.JCExpression>nil(),
								qualifiedName("$org_powerassert_recorderRuntime", "recordExpression"),
								List.of(
										treeMaker.Literal(source(meth)),
										recorded,
										treeMaker.Literal(meth.getStartPosition())
								)
						)
				);

				// so that we don't disrupt IDE debugging, give the instrumented expression the same position as the original
				instrumented.setPos(statement.pos);

				return replaceWithInstrumented(statement, instrumented);
			}
		}

		return super.visitMethodInvocation(node, context);
	}

	private List<JCTree.JCExpression> recordEach(List<JCTree.JCExpression> exprs) {
		java.util.List<JCTree.JCExpression> recorded = new ArrayList<>();
		for (JCTree.JCExpression arg : exprs) {
			recorded.add(recordAllValues(arg, null));
		}
		return List.from(recorded.toArray(new JCTree.JCExpression[recorded.size()]));
	}

	private static String methodName(JCTree.JCMethodInvocation meth) {
		JCTree.JCExpression methodSelect = meth.getMethodSelect();
		if(methodSelect instanceof JCTree.JCIdent) {
			return ((JCTree.JCIdent) methodSelect).name.toString();
		}
		else if(methodSelect instanceof JCTree.JCFieldAccess) {
			return ((JCTree.JCFieldAccess) methodSelect).name.toString();
		}
		return null;
	}

	@Override
	public TreePath visitAssert(AssertTree node, Context context) {
		JCTree.JCAssert assertStatement = (JCTree.JCAssert) node;
		JCTree.JCExpression assertCondition = assertStatement.getCondition();

		JCTree.JCExpressionStatement instrumented = treeMaker.Exec(
				treeMaker.Apply(
						List.<JCTree.JCExpression>nil(),
						qualifiedName("$org_powerassert_recorderRuntime", "recordExpression"),
						List.of(
								treeMaker.Literal(source(assertCondition)),
								recordAllValues(assertCondition, null),
								treeMaker.Literal(assertCondition.getStartPosition())
						)
				)
		);

		// so that we don't disrupt IDE debugging, give the instrumented expression the same position as the original
		instrumented.setPos(assertCondition.pos);

		return replaceWithInstrumented(assertStatement, instrumented);
	}

	private TreePath replaceWithInstrumented(JCTree.JCStatement statement, JCTree.JCExpressionStatement instrumented) {
		TreePath parent = getCurrentPath().getParentPath();
		while(parent != null && !(parent.getLeaf() instanceof JCTree.JCBlock)) {
			parent = parent.getParentPath();
		}

		if(parent == null) {
			// TODO is this case possible?
			return null;
		}

		JCTree.JCBlock containingBlock = (JCTree.JCBlock) parent.getLeaf();
		JCTree.JCBlock powerAssertBlock = treeMaker.Block(0, List.of(newRecorderRuntime(), instrumented));
		containingBlock.stats = replaceStatement(containingBlock.getStatements(), statement, powerAssertBlock);

		return null;
	}

	private JCTree.JCVariableDecl newRecorderRuntime() {
		JCTree.JCExpression recorderRuntimeType = qualifiedName("org", "powerassert", "synthetic", "RecorderRuntime");
		return treeMaker.VarDef(
					treeMaker.Modifiers(Flags.FINAL),
					name("$org_powerassert_recorderRuntime"), // name that likely won't collide with any other
					recorderRuntimeType,
					treeMaker.NewClass(null, List.<JCTree.JCExpression>nil(), recorderRuntimeType, List.<JCTree.JCExpression>nil(), null)
			);
	}

	private JCTree.JCExpression recordAllValues(JCTree.JCExpression expr, JCTree.JCExpression parent) {
		if(expr instanceof JCTree.JCBinary) {
			JCTree.JCBinary binary = (JCTree.JCBinary) expr;
			return recordValue(
					treeMaker.Binary(
							binary.getTag(),
							recordAllValues(binary.getLeftOperand(), expr),
							recordAllValues(binary.getRightOperand(), expr)
					).setPos(binary.pos),
					binary.getRightOperand().pos - 2
			);
		}
		else if(expr instanceof JCTree.JCUnary) {
			JCTree.JCUnary unary = (JCTree.JCUnary) expr;
			return recordValue(
					treeMaker.Unary(
						unary.getTag(),
						recordAllValues(unary.getExpression(), expr)
					).setPos(unary.pos),
					unary.getExpression().pos - 1
			);
		}
		else if(expr instanceof JCTree.JCMethodInvocation) {
			JCTree.JCMethodInvocation method = (JCTree.JCMethodInvocation) expr;
			return recordValue(
					// oddly, methodSelect is expressed as a JCFieldAccess, and if we recurse through this expression,
					// we will attempt to record the method name as a field access, which it is not... so do
					// not recurse on method select
					treeMaker.Apply(
							method.typeargs,
							recordAllValues(method.getMethodSelect(), expr),
							recordArgs(method.args, expr)
					).setPos(method.pos),
					method.getMethodSelect().pos + 1
			);
		}
		else if(expr instanceof JCTree.JCIdent) {
			String name = ((JCTree.JCIdent) expr).getName().toString();

			// differentiate between class name identifiers and variable identifiers
			boolean staticMethodTarget = elements.getTypeElement(name) != null || elements.getTypeElement("java.lang." + name) != null;

			if(!staticMethodTarget && !(parent instanceof JCTree.JCMethodInvocation)) {
				return recordValue(expr, expr.pos);
			}
			return expr;
		}
		else if(expr instanceof JCTree.JCFieldAccess) {
			JCTree.JCFieldAccess field = (JCTree.JCFieldAccess) expr;
			if(!(field.selected instanceof JCTree.JCLiteral)) {
				JCTree.JCExpression recordedField = treeMaker.Select(
						recordAllValues(field.getExpression(), expr),
						field.name
				).setPos(field.pos);

				if(parent != null && parent instanceof JCTree.JCMethodInvocation) {
					// when the parent is a method invocation, this is not a true "field access", so don't attempt
					// to record the value... instead the recording of the result of the method invocation will capture
					// its output
					return recordedField;
				}
				else {
					return recordValue(
							recordedField,
							expr.pos + 1);
				}
			}
			return expr;
		}
		else if(expr instanceof JCTree.JCNewClass) {
			JCTree.JCNewClass newClass = (JCTree.JCNewClass) expr;
			return treeMaker.NewClass(
					recordAllValues(newClass.encl, expr),
					newClass.typeargs,
					newClass.clazz,
					recordArgs(newClass.args, expr),
					newClass.def
			).setPos(newClass.pos);
		}
		else if(expr instanceof JCTree.JCArrayAccess) {
			JCTree.JCArrayAccess arrayAccess = (JCTree.JCArrayAccess) expr;
			return recordValue(
					treeMaker.Indexed(
						recordAllValues(arrayAccess.getExpression(), expr),
						recordAllValues(arrayAccess.getIndex(), expr)
					).setPos(arrayAccess.pos),
					expr.pos
			);
		}
		else if(expr instanceof JCTree.JCNewArray) {
			JCTree.JCNewArray newArray = (JCTree.JCNewArray) expr;
			return treeMaker.NewArray(
					recordAllValues(newArray.getType(), expr),
					recordArgs(newArray.getDimensions(), expr),
					recordArgs(newArray.getInitializers(), expr)
			).setPos(newArray.pos);
		}
		else if(expr instanceof JCTree.JCConditional) {
			JCTree.JCConditional conditional = (JCTree.JCConditional) expr;
			return recordValue(
					treeMaker.Conditional(
							recordAllValues(conditional.getCondition(), expr),
							recordAllValues(conditional.getTrueExpression(), expr),
							recordAllValues(conditional.getFalseExpression(), expr)
					).setPos(conditional.pos),
					expr.pos
			);
		}
		return expr;
	}

	private JCTree.JCExpression recordValue(JCTree.JCExpression expr, int anchor) {
		return treeMaker.Apply(
			List.<JCTree.JCExpression>nil(),
			qualifiedName("$org_powerassert_recorderRuntime", "recordValue"),
			List.of(expr, treeMaker.Literal(anchor))
		);
	}

	private JCTree.JCExpression qualifiedName(String... name) {
		JCTree.JCExpression prior = treeMaker.Ident(elements.getName(name[0]));
		for(int i = 1; i < name.length; i++) {
			prior = treeMaker.Select(prior, elements.getName(name[i]));
		}
		return prior;
	}

	private Name name(String name) {
		return elements.getName(name);
	}

	private List<JCTree.JCStatement> replaceStatement(List<JCTree.JCStatement> list, JCTree.JCStatement replace, JCTree.JCStatement with) {
		JCTree.JCStatement[] stats = list.toArray(new JCTree.JCStatement[list.size()]);
		for(int i = 0; i < stats.length; i++) {
			if(stats[i] == replace) {
				stats[i] = with;
				break;
			}
		}
		return List.from(stats);
	}

	/**
	 * @return the raw source of expr, extracted from the raw source itself since JCExpression's toString()
	 * normalizes whitespace but positions still refer to the position in source prior to this normalization
	 */
	private String source(JCTree.JCExpression expr) {
		String exprStr = expr.toString();
		int sourcePos = expr.getStartPosition();

		for(int exprPos = 0; exprPos < exprStr.length();) {
			char exprChar = exprStr.charAt(exprPos);
			char sourceChar = rawSource.charAt(sourcePos);

			if(Character.isWhitespace(exprChar)) {
				exprPos++;
				continue;
			}
			if(Character.isWhitespace(sourceChar)) {
				sourcePos++;
				continue;
			}
			exprPos++;
			sourcePos++;
		}

		return rawSource.subSequence(expr.getStartPosition(), sourcePos).toString();
	}

	private List<JCTree.JCExpression> recordArgs(List<JCTree.JCExpression> args, JCTree.JCExpression parent) {
		JCTree.JCExpression[] recordedArgs = new JCTree.JCExpression[args.length()];
		for(int i = 0; i < args.length(); i++) {
			recordedArgs[i] = recordAllValues(args.get(i), parent);
		}
		return List.from(recordedArgs);
	}
}
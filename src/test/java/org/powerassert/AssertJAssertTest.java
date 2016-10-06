package org.powerassert;

import org.junit.Test;

public class AssertJAssertTest extends AbstractAssertTest {

  @Test
  public void assertJAssertThatIsTrue() {
    java.compile(
        "import static org.junit.Assert.*;\n" +
            "public class A {\n" +
            "	@org.junit.Test public void test() {\n" +
            "      org.assertj.core.api.Assertions.assertThat(Character.isWhitespace(\"abc\".charAt(0))).isTrue();\n" +
            "	}\n" +
            "}\n");

    testFailsWithMessage("A", "test",
        "org.assertj.core.api.Assertions.assertThat(Character.isWhitespace(\"abc\".charAt(0))).isTrue()",
        "                                                     |                  |",
        "                                                     false              a");
  }

  @Test
  public void assertJAssertThatIsEqualTo(){
    java.compile(
        "import static org.junit.Assert.*;\n" +
            "public class A {\n" +
            "	@org.junit.Test public void test() {\n" +
            "      String actual = \"Gandalf\";\n" +
            "      String expected = \"Frodo\";\n" +
            "      org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);\n" +
            "	}\n" +
            "}\n");

    testFailsWithMessage("A", "test",
        "org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected)",
        "                                           |                 |",
        "                                           Gandalf           Frodo");
  }

  @Test
  public void assertJAssertThatIsNotEqualTo(){
    java.compile(
        "import static org.junit.Assert.*;\n" +
            "public class A {\n" +
            "	@org.junit.Test public void test() {\n" +
            "      String actual = \"Gandalf\";\n" +
            "      org.assertj.core.api.Assertions.assertThat(actual).isNotEqualTo(actual);\n" +
            "	}\n" +
            "}\n");

    testFailsWithMessage("A", "test",
        "org.assertj.core.api.Assertions.assertThat(actual).isNotEqualTo(actual)",
        "                                           |                    |",
        "                                           Gandalf              Gandalf");
  }
}

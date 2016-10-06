package org.powerassert.synthetic.assertj;

import org.junit.ComparisonFailure;

public class AssertThat {
  public static boolean isTrue(boolean actual){
    try {
      org.assertj.core.api.Assertions.assertThat(actual).isTrue();
      return true;
    }catch(ComparisonFailure e){
      return false;
    }
  }

  public static boolean isEqualTo(String actual, String expected){
    try {
      org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
      return true;
    }catch(ComparisonFailure e){
      return false;
    }
  }

  public static boolean isNotEqualTo(String actual, String expected){
    return !isEqualTo(actual, expected);
  }
}

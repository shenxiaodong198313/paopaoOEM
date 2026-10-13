package com.paopaoime.aikeyboard.ime.libs.pinyin4j.format;

/**
 * Define the output format of character '眉'
 * 
 * <p>
 * '眉' is a special character of Hanyu Pinyin, which can not be simply
 * represented by English letters. In Hanyu Pinyin, such characters include '眉',
 * '眉e', '眉an', and '眉n'.
 * 
 * <p>
 * This class provides several options for output of '眉', which are listed
 * below.
 * 
 * <table>
 * <tr>
 * <th>Options</th>
 * <th>Output</th>
 * </tr>
 * <tr>
 * <td>WITH_U_AND_COLON</td>
 * <td align = "center">u:</td>
 * </tr>
 * <tr>
 * <td>WITH_V</td>
 * <td align = "center">v</td>
 * </tr>
 * <tr>
 * <td>WITH_U_UNICODE</td>
 * <td align = "center">眉</td>
 * </tr>
 * </table>
 * 
 * @author Li Min (xmlerlimin@gmail.com)
 * 
 */
public class HanyuPinyinVCharType {

  /**
   * The option indicates that the output of '眉' is "u:"
   */
  public static final HanyuPinyinVCharType WITH_U_AND_COLON =
      new HanyuPinyinVCharType("WITH_U_AND_COLON");

  /**
   * The option indicates that the output of '眉' is "v"
   */
  public static final HanyuPinyinVCharType WITH_V = new HanyuPinyinVCharType("WITH_V");

  /**
   * The option indicates that the output of '眉' is "眉" in Unicode form
   */
  public static final HanyuPinyinVCharType WITH_U_UNICODE =
      new HanyuPinyinVCharType("WITH_U_UNICODE");

  /**
   * @return Returns the name.
   */
  public String getName() {
    return name;
  }

  /**
   * @param name
   *            The name to set.
   */
  protected void setName(String name) {
    this.name = name;
  }

  /**
   * Constructor
   */
  protected HanyuPinyinVCharType(String name) {
    setName(name);
  }

  protected String name;
}


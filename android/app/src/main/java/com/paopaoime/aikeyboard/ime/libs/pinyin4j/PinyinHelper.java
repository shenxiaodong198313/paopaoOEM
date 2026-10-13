package com.paopaoime.aikeyboard.ime.libs.pinyin4j;

import com.paopaoime.aikeyboard.ime.libs.pinyin4j.format.HanyuPinyinOutputFormat;
import com.paopaoime.aikeyboard.ime.libs.pinyin4j.multipinyin.Trie;

public class PinyinHelper {

    /**
     * 姹夊瓧杞畝鎷?
     */
    public static String getPinYinHeadChar(String str) {
        ChineseToPinyinResource resource = ChineseToPinyinResource.getInstance();
        StringBuilder resultPinyinStrBuf = new StringBuilder();
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            String result = null;//鍖归厤鍒扮殑鏈€闀跨殑缁撴灉
            char ch = chars[i];
            Trie currentTrie = resource.getUnicodeToHanyuPinyinTable();
            int success = i;
            int current = i;
            do {
                String hexStr = Integer.toHexString(ch).toUpperCase();
                currentTrie = currentTrie.get(hexStr);
                if (currentTrie != null) {
                    if (currentTrie.getPinyin() != null) {
                        result = currentTrie.getPinyin();
                        success = current;
                    }
                    currentTrie = currentTrie.getNextTire();
                }
                current++;
                if (current < chars.length) ch = chars[current];
                else break;
            }
            while (currentTrie != null);
            if (result == null) {//濡傛灉鍦ㄥ墠缂€鏍戜腑娌℃湁鍖归厤鍒帮紝閭ｄ箞瀹冨氨涓嶈兘杞崲涓烘嫾闊筹紝鐩存帴杈撳嚭鎴栬€呭幓鎺?
                if (Character.isLetter(chars[i])) resultPinyinStrBuf.append(chars[i]);
            } else {
                String[] pinyinStrArray = resource.parsePinyinString(result);
                if (pinyinStrArray != null) {
                    for (String s : pinyinStrArray) {
                        resultPinyinStrBuf.append(s.charAt(0));
                        if (i == success) break;
                    }
                }
            }
            i = success;
            if(resultPinyinStrBuf.length() >= 4)break;
        }
        return resultPinyinStrBuf.toString();
    }

    /**
     * 姹夊瓧杞嫾闊?
     */
    static public String toHanYuPinyin(String str, HanyuPinyinOutputFormat outputFormat, String separate) {
        ChineseToPinyinResource resource = ChineseToPinyinResource.getInstance();
        StringBuilder resultPinyinStrBuf = new StringBuilder();
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            String result = null;//鍖归厤鍒扮殑鏈€闀跨殑缁撴灉
            char ch = chars[i];
            Trie currentTrie = resource.getUnicodeToHanyuPinyinTable();
            int success = i;
            int current = i;
            do {
                String hexStr = Integer.toHexString(ch).toUpperCase();
                currentTrie = currentTrie.get(hexStr);
                if (currentTrie != null) {
                    if (currentTrie.getPinyin() != null) {
                        result = currentTrie.getPinyin();
                        success = current;
                    }
                    currentTrie = currentTrie.getNextTire();
                }
                current++;
                if (current < chars.length)
                    ch = chars[current];
                else
                    break;
            }
            while (currentTrie != null);
            if (result != null) {
                String[] pinyinStrArray = resource.parsePinyinString(result);
                if (pinyinStrArray != null) {
                    for (int j = 0; j < pinyinStrArray.length; j++) {
                        resultPinyinStrBuf.append(PinyinFormatter.formatHanyuPinyin(pinyinStrArray[j], outputFormat));
                        if (current < chars.length || (j < pinyinStrArray.length - 1 && i != success)) {
                            resultPinyinStrBuf.append(separate);
                        }
                        if (i == success)
                            break;
                    }
                }
            }
            i = success;
        }

        return resultPinyinStrBuf.toString();
    }


    /**
     * 姹夊瓧杞嫾闊?鏀寔鍗曞瓧澶氶煶
     */
    static public String toHanYuPinyinMulti(String str, HanyuPinyinOutputFormat outputFormat, String separate) {
        if(str.length() == 1){
            separate =  ",";
        }
        ChineseToPinyinResource resource = ChineseToPinyinResource.getInstance();
        StringBuilder resultPinyinStrBuf = new StringBuilder();
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            String result = null;//鍖归厤鍒扮殑鏈€闀跨殑缁撴灉
            char ch = chars[i];
            Trie currentTrie = resource.getUnicodeToHanyuPinyinTable();
            int success = i;
            int current = i;
            do {
                String hexStr = Integer.toHexString(ch).toUpperCase();
                currentTrie = currentTrie.get(hexStr);
                if (currentTrie != null) {
                    if (currentTrie.getPinyin() != null) {
                        result = currentTrie.getPinyin();
                        success = current;
                    }
                    currentTrie = currentTrie.getNextTire();
                }
                current++;
                if (current < chars.length)
                    ch = chars[current];
                else
                    break;
            } while (currentTrie != null);
            if (result != null) {
                String[] pinyinStrArray = resource.parsePinyinString(result);
                if (pinyinStrArray != null) {
                    for (String s : pinyinStrArray) {
                        resultPinyinStrBuf.append(PinyinFormatter.formatHanyuPinyin(s, outputFormat)).append(separate);
                        if (i == success && str.length() != 1) break;
                    }
                }
            }
            i = success;
        }
        if(resultPinyinStrBuf.length() > 1)resultPinyinStrBuf.deleteCharAt(resultPinyinStrBuf.length() -1);
        return  resultPinyinStrBuf.toString();
    }


    // ! Hidden constructor
    private PinyinHelper() {
    }
}


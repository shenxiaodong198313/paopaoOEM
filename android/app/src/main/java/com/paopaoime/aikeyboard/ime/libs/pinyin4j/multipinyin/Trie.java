package com.paopaoime.aikeyboard.ime.libs.pinyin4j.multipinyin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Hashtable;

public class Trie {

    private final Hashtable<String, Trie> values = new Hashtable<String, Trie>();//鏈妭鐐瑰寘鍚殑鍊?

    private String pinyin;//鏈妭鐐圭殑鎷奸煶

    private Trie nextTire;//涓嬩竴涓妭鐐?涔熷氨鏄尮閰嶄笅涓€涓瓧绗?

    public String getPinyin() {
        return pinyin;
    }

    public void setPinyin(String pinyin) {
        this.pinyin = pinyin;
    }

    public Trie getNextTire() {
        return nextTire;
    }

    public void setNextTire(Trie nextTire) {
        this.nextTire = nextTire;
    }

    /**
     * 鍔犺浇鎷奸煶
     *
     * @param inStream 鎷奸煶鏂囦欢杈撳叆娴?
     * @throws IOException
     */
    public synchronized void load(InputStream inStream) throws IOException {
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(inStream);
            bufferedReader = new BufferedReader(inputStreamReader);
            String s;
            while ((s = bufferedReader.readLine()) != null) {
                String[] keyAndValue = s.split(" ");
                if (keyAndValue.length != 2)
                    continue;
                Trie trie = new Trie();
                trie.pinyin = keyAndValue[1];
                put(keyAndValue[0], trie);
            }
        } finally {
            if (inputStreamReader != null)
                inputStreamReader.close();
            if (bufferedReader != null)
                bufferedReader.close();
        }
    }

    /**
     * 鍔犺浇澶氶煶瀛楁嫾闊宠瘝鍏?
     *
     * @param inStream 鎷奸煶鏂囦欢杈撳叆娴?
     */
    public synchronized void loadMultiPinyin(InputStream inStream) throws IOException {
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(inStream);
            bufferedReader = new BufferedReader(inputStreamReader);
            String s;
            while ((s = bufferedReader.readLine()) != null) {
                String[] keyAndValue = s.split(" ");
                if (keyAndValue.length != 2)
                    continue;

                String key = keyAndValue[0];//澶氫簬涓€涓瓧鐨勫瓧绗︿覆
                String value = keyAndValue[1];//瀛楃涓茬殑鎷奸煶
                char[] keys = key.toCharArray();

                Trie currentTrie = this;
                for (int i = 0; i < keys.length; i++) {
                    String hexString = Integer.toHexString(keys[i]).toUpperCase();

                    Trie trieParent = currentTrie.get(hexString);
                    if (trieParent == null) {//濡傛灉娌℃湁姝ゅ€?鐩存帴put杩涘幓涓€涓┖瀵硅薄
                        currentTrie.put(hexString, new Trie());
                        trieParent = currentTrie.get(hexString);
                    }
                    Trie trie = trieParent.getNextTire();//鑾峰彇姝ゅ璞＄殑涓嬩竴涓?

                    if (keys.length - 1 == i) {//鏈€鍚庝竴涓瓧浜?闇€瑕佹妸鎷奸煶鍐欒繘鍘?
                        trieParent.pinyin = value;
                        break;//姝よ鍏跺疄骞舵病鏈夋剰涔?
                    }

                    if (trie == null) {
                        if (keys.length - 1 != i) {
                            //涓嶆槸鏈€鍚庝竴涓瓧,鍐欏叆杩欎釜瀛楃殑nextTrie,骞跺尮閰嶄笅涓€涓?
                            Trie subTrie = new Trie();
                            trieParent.setNextTire(subTrie);
                            subTrie.put(Integer.toHexString(keys[i + 1]).toUpperCase(), new Trie());
                            currentTrie = subTrie;
                        }
                    } else {
                        currentTrie = trie;
                    }

                }
            }
        } finally {
            if (inputStreamReader != null)
                inputStreamReader.close();
            if (bufferedReader != null)
                bufferedReader.close();
        }
    }

    public Trie get(String hexString) {
        return values.get(hexString);
    }

    public void put(String s, Trie trie) {
        values.put(s, trie);
    }
}


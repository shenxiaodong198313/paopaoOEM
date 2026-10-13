/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * T9 key (digit 2-9) to pinyin mapping for pinyin selection bar.
 */
package org.fcitx.fcitx5.android.input.t9

object T9PinyinUtils {

    private val digitToGroup = "ADGJMPTW"

    private fun digitToGroupChar(digit: Char): Char =
        if (digit in '2'..'9') digitToGroup[digit - '2'] else digit

    private fun groupSequenceFromDigits(digits: String): String =
        digits.map { digitToGroupChar(it) }.joinToString("")

    private val pinyinMap = buildMap {
        put("A", "a,b,c")
        put("D", "e,d,f")
        put("G", "g,h,i")
        put("J", "j,k,l")
        put("M", "o,m,n")
        put("P", "p,q,r,s")
        put("T", "t,u,v")
        put("W", "w,x,y,z")
        put("AA", "ba,ca")
        put("AD", "ce")
        put("AG", "ai,bi,ci,ch")
        put("AM", "an,ao,bo")
        put("AT", "bu,cu")
        put("DA", "da,fa")
        put("DD", "de")
        put("DG", "di,ei,eh")
        put("DM", "en,fo")
        put("DP", "er")
        put("DT", "du,fu")
        put("GA", "ga,ha")
        put("GD", "ge,he")
        put("GT", "gu,hu")
        put("JA", "ka,la")
        put("JD", "ke,le")
        put("JG", "ji,li")
        put("JM", "lo")
        put("JT", "ju,ku,lu,lv")
        put("MA", "ma,na")
        put("MD", "me,ne")
        put("MG", "mi,ni")
        put("MM", "mo")
        put("MT", "mu,nu,nv,ou")
        put("PA", "pa,sa")
        put("PD", "re,se")
        put("PG", "pi,qi,ri,si,sh")
        put("PM", "po")
        put("PT", "pu,qu,ru,su")
        put("TA", "ta")
        put("TD", "te")
        put("TG", "ti")
        put("TT", "tu")
        put("WA", "wa,ya,za")
        put("WD", "ye,ze")
        put("WG", "xi,yi,zi")
        put("WM", "wo,yo")
        put("WT", "wu,xu,yu,zu")
        put("AAG", "bai,cai")
        put("AAM", "ban,bao,can,cao")
        put("ADG", "bei,cei")
        put("ADM", "ben,cen")
        put("AGA", "cha")
        put("AGD", "bie,che")
        put("AGG", "chi")
        put("AGM", "bin")
        put("AGT", "chu")
        put("AMG", "ang")
        put("AMT", "cou")
        put("ATG", "cui")
        put("ATM", "cun,cuo")
        put("DAG", "dai")
        put("DAM", "dan,dao,fan")
        put("DDG", "dei,fei")
        put("DDM", "den,fen")
        put("DGA", "dia")
        put("DGD", "die")
        put("DGT", "diu")
        put("DMG", "eng")
        put("DMT", "dou,fou")
        put("DTG", "dui")
        put("DTM", "dun,duo")
        put("GAG", "gai,hai")
        put("GAM", "gan,gao,han,hao")
        put("GDG", "gei,hei")
        put("GDM", "gen,hen")
        put("GMT", "gou,hou")
        put("GTA", "gua,hua")
        put("GTG", "gui,hui")
        put("GTM", "gun,guo,hun,huo")
        put("JAG", "kai,lai")
        put("JAM", "kan,kao,lan,lao")
        put("JDG", "kei,lei")
        put("JDM", "ken")
        put("JGA", "jia,lia")
        put("JGD", "jie,lie")
        put("JGM", "jin,lin")
        put("JGT", "jiu,liu")
        put("JMT", "kou,lou")
        put("JTA", "kua")
        put("JTD", "jue,lue,lve")
        put("JTG", "kui")
        put("JTM", "jun,kun,kuo,lun,luo")
        put("MAG", "mai,nai")
        put("MAM", "man,mao,nan,nao")
        put("MDG", "mei,nei")
        put("MDM", "men,nen")
        put("MGD", "mie,nie")
        put("MGM", "min,nin")
        put("MGT", "miu,niu")
        put("MMT", "mou,nou")
        put("MTD", "nue,nve")
        put("MTM", "nuo,nun")
        put("PAG", "pai,sai")
        put("PAM", "pan,pao,ran,rao,san,sao")
        put("PDG", "pei,sei")
        put("PDM", "pen,ren,sen")
        put("PGA", "qia,sha,pia")
        put("PGD", "pie,qie,she")
        put("PGG", "shi")
        put("PGM", "pin,qin")
        put("PGT", "qiu,shu")
        put("PMT", "pou,rou,sou")
        put("PTD", "que")
        put("PTG", "rui,sui")
        put("PTM", "qun,run,ruo,sun,suo")
        put("TAG", "tai")
        put("TAM", "tan,tao")
        put("TDG", "tei")
        put("TGD", "tie")
        put("TMT", "tou")
        put("TTG", "tui")
        put("TTM", "tun,tuo")
        put("WAG", "wai,zai,yai")
        put("WAM", "wan,yan,yao,zan,zao")
        put("WDG", "wei,zei")
        put("WDM", "wen,zen")
        put("WGA", "xia,zha")
        put("WGD", "xie,zhe")
        put("WGG", "zhi")
        put("WGM", "xin,yin")
        put("WGT", "xiu,zhu")
        put("WMT", "you,zou")
        put("WTD", "xue,yue")
        put("WTG", "zui")
        put("WTM", "xun,yun,zun,zuo")
    }

    fun t9KeyToPinyin(t9DigitSequence: String?): List<String> {
        if (t9DigitSequence.isNullOrEmpty()) return emptyList()
        val digits = t9DigitSequence.filter { it in '2'..'9' }
        if (digits.isEmpty()) return emptyList()
        val groupSeq = groupSequenceFromDigits(digits)
        val result = mutableListOf<String>()
        for (len in groupSeq.length downTo 1) {
            val prefix = groupSeq.substring(0, len)
            pinyinMap[prefix]?.let { value ->
                value.split(",").forEach { p ->
                    if (p !in result) result.add(p)
                }
            }
        }
        return result
    }

    fun matchedPrefixLength(t9DigitSequence: String?, pinyin: String?): Int {
        if (t9DigitSequence.isNullOrEmpty() || pinyin.isNullOrEmpty()) return 0
        val digits = t9DigitSequence.filter { it in '2'..'9' }
        if (digits.isEmpty()) return 0
        val groupSeq = groupSequenceFromDigits(digits)
        for (len in groupSeq.length downTo 1) {
            val prefix = groupSeq.substring(0, len)
            val matches = pinyinMap[prefix]?.split(",") ?: continue
            if (matches.any { it == pinyin }) {
                return len
            }
        }
        return 0
    }

    private val groupToDigit = mapOf(
        'A' to '2', 'D' to '3', 'G' to '4', 'J' to '5',
        'M' to '6', 'P' to '7', 'T' to '8', 'W' to '9'
    )

    fun pinyinToT9Keys(pinyin: String?): String {
        if (pinyin.isNullOrEmpty()) return ""
        for ((key, value) in pinyinMap) {
            val pinyinList = value.split(",")
            if (pinyinList.any { it == pinyin }) {
                return key.map { group -> groupToDigit[group] ?: group }.joinToString("")
            }
        }
        return ""
    }
}

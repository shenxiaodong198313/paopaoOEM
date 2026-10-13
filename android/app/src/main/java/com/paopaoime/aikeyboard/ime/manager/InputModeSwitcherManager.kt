package com.paopaoime.aikeyboard.ime.manager

import android.view.inputmethod.EditorInfo
import com.paopaoime.aikeyboard.ime.application.CustomConstant
import com.paopaoime.aikeyboard.ime.prefs.AppPrefs.Companion.getInstance
import com.paopaoime.aikeyboard.ime.keyboard.KeyboardManager
import com.paopaoime.aikeyboard.ime.engine.Kernel

/**
 * 输入法模式转换器。设置输入法的软键盘。
 */
object InputModeSwitcherManager {

    /**
     * User defined key code, used by soft keyboard.
     * 用户定义的key的code，用于软键盘、shift键的code。
     */
    const val USER_DEF_KEYCODE_SHIFT_1 = -1

    /**
     * User defined key code, used by soft keyboard. 语言键的code,语言切换键。
     */
    const val USER_DEF_KEYCODE_LANG_2 = -2

    /**
     * User defined key code, used by soft keyboard. 语言键的code,符号键盘切换键。
     */
    const val USER_DEF_KEYCODE_SYMBOL_3 = -3

    /**
     * User defined key code, used by soft keyboard. 语言键的code,表情键盘切换键。
     */
    const val USER_DEF_KEYCODE_EMOJI_4 = -4

    /**
     * User defined key code, used by soft keyboard. 语言键的code,数字键盘切换键。
     */
    const val USER_DEF_KEYCODE_NUMBER_5 = -5

    /**
     * User defined key code, used by soft keyboard. 语言键的code,数字键盘返回按键。
     */
    const val USER_DEF_KEYCODE_RETURN_6 = -6

    /**
     * User defined key code, used by soft keyboard. 语言键的code,编辑键盘。
     */
    const val USER_DEF_KEYCODE_TEXTEDIT_7 = -7

    /**
     * User defined key code, used by soft keyboard. 表情逗号。
     */
    const val USER_DEF_KEYCODE_EMOJI_8 = -8

    /**
     * User defined key code, used by soft keyboard. 方向控制。或谷歌键盘的重输按钮
     */
    const val USER_DEF_KEYCODE_CURSOR_DIRECTION_9 = -9

    /**
     * User defined key code, used by soft keyboard. 语言键的code,九宫格、手写符号侧栏占位符。
     */
    const val USER_DEF_KEYCODE_LEFT_SYMBOL_12 = -12

    /**
     * User defined key code, used by soft keyboard. 语言键的code,逗号（中文：，英文：,）。
     */
    const val USER_DEF_KEYCODE_LEFT_COMMA_13 = -13
    /**
     * User defined key code, used by soft keyboard. 语言键的code,句号（中文：。英文：.）。
     */
    const val USER_DEF_KEYCODE_LEFT_PERIOD_14 = -14
    /**
     * User defined key code, used by soft keyboard. 语言键的code,星号（*）。
     */
    const val USER_DEF_KEYCODE_STAR_17 = -17

    /**
     * User defined key code, used by soft keyboard. 编辑键盘方向
     */
    const val USER_DEF_KEYCODE_SELECT_MODE = -18
    const val USER_DEF_KEYCODE_SELECT_ALL = -19
    const val USER_DEF_KEYCODE_CUT = -20
    const val USER_DEF_KEYCODE_COPY = -21
    const val USER_DEF_KEYCODE_PASTE = -22
    const val USER_DEF_KEYCODE_MOVE_START = -23
    const val USER_DEF_KEYCODE_MOVE_END = -24


    /**
     * Bits used to indicate soft keyboard layout. If none bit is set, the
     * current input mode does not require a soft keyboard.
     * 第8位指明软键盘的布局。如果最8位为0，那么就表明当前输入法模式不需要软键盘。
     */
    const val MASK_SKB_LAYOUT = 0xff00 //不同位代表意义 布局（8位）/语言/大小写 0/0/0/0

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 鎸囨槑鏍囧噯鐨勪紶缁熼敭鐩?鎷奸煶
     */
    const val MASK_SKB_LAYOUT_QWERTY_PINYIN = 0x1000

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 指明九宫格软键盘，拼音
     */
    const val MASK_SKB_LAYOUT_T9_PINYIN = 0x2000

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 指明手写键
     */
    const val MASK_SKB_LAYOUT_HANDWRITING = 0x3000

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 指明标准的传统键盘
     */
    const val MASK_SKB_LAYOUT_QWERTY_ABC = 0x4000

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 指明数字键
     */
    const val MASK_SKB_LAYOUT_NUMBER = 0x5000

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 指明乱序17
     */
    const val MASK_SKB_LAYOUT_LX17 = 0x6000

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 指明笔画键盘
     */
    const val MASK_SKB_LAYOUT_STROKE = 0x7000

    /**
     * A kind of soft keyboard layout. An input mode should be anded with
     * [.MASK_SKB_LAYOUT] to get its soft keyboard layout. 指明文本编辑键盘
     */
    const val MASK_SKB_LAYOUT_TEXTEDIT= 0x8000

    /**
     * 指明语言。
     */
    private const val MASK_LANGUAGE = 0x00f0

    /**
     * Used to indicate the current language. An input mode should be anded with
     * [.MASK_LANGUAGE] to get this information. 指明中文语言。
     */
    const val MASK_LANGUAGE_CN = 0x0010

    /**
     * Used to indicate the current language. An input mode should be anded with
     * [.MASK_LANGUAGE] to get this information. 指明英文语言。
     */
    private const val MASK_LANGUAGE_EN = 0x0020

    /**
     * 低4位指明软键盘当前的状态，比如高（大写），低（小写）。
     */
    private const val MASK_CASE = 0x000f

    /**
     * 指明软键盘状态为低（小写）。
     */
    private const val MASK_CASE_LOWER = 0x0000

    /**
     * 指明软键盘状态为高（大写）。
     */
    const val MASK_CASE_UPPER = 0x0001

    /**
     * 指明软键盘状态为高（大写）锁定状态。
     */
    private const val MASK_CASE_UPPER_LOCK = 0x0002

    /**
     * Mode for inputing Chinese with soft keyboard. 九宫格软键盘、中文模式
     */
    const val MODE_T9_CHINESE = MASK_SKB_LAYOUT_T9_PINYIN or MASK_LANGUAGE_CN

    /**
     * Mode for inputing Chinese with soft keyboard. 全键盘、中文模式
     */
    const val MODE_QWERTY_CHINESE = MASK_SKB_LAYOUT_QWERTY_PINYIN or MASK_LANGUAGE_CN

    /**
     * Mode for inputing Chinese with soft keyboard. 手写软键盘、中文模式
     */
    const val MODE_HANDWRITING_CHINESE = MASK_SKB_LAYOUT_HANDWRITING or MASK_LANGUAGE_CN

    /**
     * Mode for inputing Chinese with soft keyboard. 笔画键盘、中文模式
     */
    const val MODE_STROKE_CHINESE = MASK_SKB_LAYOUT_STROKE or MASK_LANGUAGE_CN

    /**
     * Mode for inputing English lower characters with soft keyboard. 标准软键盘、英文、小写模式
     */
    const val MODE_ENGLISH_LOWER =
        MASK_SKB_LAYOUT_QWERTY_ABC or MASK_LANGUAGE_EN or MASK_CASE_LOWER

    /**
     * Mode for inputing English lower characters with soft keyboard. 标准软键盘、英文、小写模式
     */
    private const val MODE_SKB_ENGLISH_LOWER =
        MODE_ENGLISH_LOWER

    /**
     * Mode for inputing English upper characters with soft keyboard. 鏍囧噯杞敭鐩樸€佽嫳鏂囥€佸ぇ鍐欒蒋閿洏妯″紡
     */
    private const val MODE_SKB_ENGLISH_UPPER =
        MASK_SKB_LAYOUT_QWERTY_ABC or MASK_LANGUAGE_EN or MASK_CASE_UPPER

    /**
     * Mode for inputing English upper characters with soft keyboard. 鏍囧噯杞敭鐩樸€佽嫳鏂囥€佸ぇ鍐欓攣瀹氳蒋閿洏妯″紡
     */
    private const val MODE_SKB_ENGLISH_UPPER_LOCK =
        MASK_SKB_LAYOUT_QWERTY_ABC or MASK_LANGUAGE_EN or MASK_CASE_UPPER_LOCK

    /**
     * Unset mode. 鏈缃緭鍏ユ硶妯″紡銆?
     */
    private const val MODE_UNSET = 0
    /**
     * The input mode for the current edit box. 褰撳墠杈撳叆娉曠殑妯″紡
     */
    private var mInputMode = MODE_UNSET

    /**
     * Used to remember recent mode to input language. 鏈€杩戠殑璇█杈撳叆娉曟ā寮?
     */
    private var mRecentLauageInputMode = MODE_UNSET

    /**
     * Used to indicate required toggling operations.
     * 鎺у埗褰撳墠杈撳叆娉曟ā寮忚蒋閿洏甯冨眬瑕佹樉绀虹殑鎸夐敭鍒囨崲鐘舵€佸拰瑕佹樉绀虹殑琛孖D銆傛瘮濡傚綋鍓嶈蒋閿洏甯冨眬涓?
     * 锛屾湁涓€涓寜閿湁榛樿鐘舵€併€佸拰涓や釜鍒囨崲鐘舵€侊紝ToggleStates涓殑mKeyStates[]淇濆瓨鐨勫氨鏄綋鍓嶈鏄剧ず鐨勫垏鎹㈢姸鎬?
     * 锛屽鏋滄寜閿殑涓や釜鍒囨崲鐘舵€侀兘娌℃湁鍦╩KeyStates[]涓?
     * 锛岄偅涔堟寜閿氨鏄剧ず榛樿鐘舵€侊紝濡傛灉涓や釜鍒囨崲鐘舵€佷腑鏈変竴涓湪mKeyStates[]涓紝灏辨樉绀洪偅涓垏鎹㈢姸鎬?
     * 銆傛敞鎰忥細缁濆涓嶅彲鑳芥湁涓€涓寜閿殑涓や釜鎴栦袱涓互涓婄殑鍒囨崲鐘舵€佸悓鏃跺湪mKeyStates
     * []涓€俆oggleStates涓嶄粎鎺у埗鎸夐敭鐨勬樉绀虹姸鎬侊紝杩樺彲浠ョ洿鎺ユ帶鍒朵竴琛屾寜閿殑鏄剧ず
     * 銆俶RowIdToEnable淇濆瓨鐨勫氨鏄彲鏄剧ず鐨勮鐨処D锛堟瘡琛岀殑ID涓嶆槸鍞竴鐨?锛屼竴涓狪D鍚屾椂璧嬪€肩粰澶氳锛夈€傚彧鏈塈D涓?
     * mRowIdToEnable 鍜?ALWAYS_SHOW_ROW_ID 鐨勮鎵嶄細琚樉绀哄嚭鏉ャ€?
     */
	@JvmField
	val mToggleStates = ToggleStates()

    /**
     * 鎺у埗褰撳墠杈撳叆娉曟ā寮忚蒋閿洏甯冨眬瑕佹樉绀虹殑鎸夐敭鍒囨崲鐘舵€佸拰瑕佹樉绀虹殑琛孖D鐨勭鐞嗙被銆傛瘮濡傚綋鍓嶈蒋閿洏甯冨眬涓?
     * 锛屾湁涓€涓寜閿湁榛樿鐘舵€併€佸拰涓や釜鍒囨崲鐘舵€侊紝ToggleStates涓殑mKeyStates[]淇濆瓨鐨勫氨鏄綋鍓嶈鏄剧ず鐨勫垏鎹㈢姸鎬?
     * 锛屽鏋滄寜閿殑涓や釜鍒囨崲鐘舵€侀兘娌℃湁鍦╩KeyStates[]涓?
     * 锛岄偅涔堟寜閿氨鏄剧ず榛樿鐘舵€侊紝濡傛灉涓や釜鍒囨崲鐘舵€佷腑鏈変竴涓湪mKeyStates[]涓紝灏辨樉绀洪偅涓垏鎹㈢姸鎬?
     * 銆傛敞鎰忥細缁濆涓嶅彲鑳芥湁涓€涓寜閿殑涓や釜鎴栦袱涓互涓婄殑鍒囨崲鐘舵€佸悓鏃跺湪mKeyStates
     * []涓€俆oggleStates涓嶄粎鎺у埗鎸夐敭鐨勬樉绀虹姸鎬侊紝杩樺彲浠ョ洿鎺ユ帶鍒朵竴琛屾寜閿殑鏄剧ず
     * 銆俶RowIdToEnable淇濆瓨鐨勫氨鏄彲鏄剧ず鐨勮鐨処D锛堟瘡琛岀殑ID涓嶆槸鍞竴鐨?锛屼竴涓狪D鍚屾椂璧嬪€肩粰澶氳锛夈€傚彧鏈塈D涓?
     * mRowIdToEnable 鍜?ALWAYS_SHOW_ROW_ID 鐨勮鎵嶄細琚樉绀哄嚭鏉ャ€?
     */
    class ToggleStates {
        @JvmField
        var charCase = MASK_CASE_LOWER
        @JvmField
		var mStateEnter = 0
    }

    val skbImeLayout: Int
        /**
         * 鏇村姞杞敭鐩橈細鍒囨崲涓鸿瑷€閿洏
         */
        get() = mRecentLauageInputMode and MASK_SKB_LAYOUT

    val skbLayout: Int
        /**
         * 鏇村姞杞敭鐩?LAYOUT锛屽垏鎹㈠洖涓婃閿洏
         */
        get() = mInputMode and MASK_SKB_LAYOUT

    // 璁板綍SHIFT鐐瑰嚮鏃堕棿锛屼綔涓哄弻鍑诲垽鏂?
    private var lsatClickTime = 0L

    // 涓枃妯″紡锛屼复鏃跺垏鎹负鑻辨枃
    private var isChineseMode = true
    /**
     * 閫氳繃鎴戜滑瀹氫箟鐨勮蒋閿洏鐨勬寜閿紝鍒囨崲杈撳叆娉曟ā寮忋€?
     */
    fun switchModeForUserKey(userKey: Int) {
        var newInputMode = MODE_UNSET
        if (USER_DEF_KEYCODE_SHIFT_1 == userKey) {
            if(isChinese && !isChineseMode)isChineseMode = true
            newInputMode = if(System.currentTimeMillis() - lsatClickTime < 300){
                MODE_SKB_ENGLISH_UPPER_LOCK
            } else if (MODE_SKB_ENGLISH_LOWER == mInputMode) {
                MODE_SKB_ENGLISH_UPPER
            } else if (MODE_SKB_ENGLISH_UPPER == mInputMode || MODE_SKB_ENGLISH_UPPER_LOCK == mInputMode){
                if(isChineseMode) getInstance().internal.inputMethodPinyinMode.getValue() else MODE_SKB_ENGLISH_LOWER
            } else {
                MODE_SKB_ENGLISH_LOWER
            }
            lsatClickTime = System.currentTimeMillis()
        } else if (USER_DEF_KEYCODE_LANG_2 == userKey) {
            newInputMode = if (isChinese) {
                isChineseMode = false
                MODE_SKB_ENGLISH_LOWER
            } else {
                getInstance().internal.inputMethodPinyinMode.getValue()
            }
        } else if (USER_DEF_KEYCODE_NUMBER_5 == userKey) {
            newInputMode = MASK_SKB_LAYOUT_NUMBER
        } else if (USER_DEF_KEYCODE_TEXTEDIT_7 == userKey) {
            newInputMode = MASK_SKB_LAYOUT_TEXTEDIT
        } else if (USER_DEF_KEYCODE_RETURN_6 == userKey) {
            newInputMode = if (mRecentLauageInputMode != 0) mRecentLauageInputMode else getInstance().internal.inputMethodPinyinMode.getValue()
        }
        // 淇濆瓨鏂扮殑杈撳叆娉曟ā寮?
        saveInputMode(newInputMode)
        KeyboardManager.instance.switchKeyboard()
    }

    /**
     * 鏍规嵁缂栬緫妗嗙殑 EditorInfo 淇℃伅鑾峰彇杞敭鐩樼殑杈撳叆娉曟ā寮忋€?
     */
    fun requestInputWithSkb(editorInfo: EditorInfo) {
        var newInputMode: Int
        when (editorInfo.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER, EditorInfo.TYPE_CLASS_PHONE, EditorInfo.TYPE_CLASS_DATETIME -> newInputMode = MASK_SKB_LAYOUT_NUMBER
            else -> {
                val v = editorInfo.inputType and EditorInfo.TYPE_MASK_VARIATION
                newInputMode = if (v == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || v == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                    || v == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || v == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                        MODE_SKB_ENGLISH_LOWER
                    } else if(getInstance().keyboardSetting.keyboardLockEnglish.getValue()){
                        getInstance().internal.inputDefaultMode.getValue()
                    } else{
                        getInstance().internal.inputMethodPinyinMode.getValue()
                    }
            }
        }
        // 鏍规嵁EditorInfo.imeOptions娣诲姞 瑕佹樉绀虹殑鎸夐敭鐨勫垏鎹㈢姸鎬?锛屼互涓嬪彧娣诲姞 Enter 閿殑鍒囨崲鐘舵€併€?
        val action = editorInfo.imeOptions and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        var enterState = 0 //EditorInfo.IME_ACTION_GO
        when (action) {
            EditorInfo.IME_ACTION_SEARCH -> enterState = 1
            EditorInfo.IME_ACTION_SEND -> enterState = 2
            EditorInfo.IME_ACTION_NEXT -> {
                val f = editorInfo.inputType and EditorInfo.TYPE_MASK_FLAGS
                if (f != EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) {
                    enterState = 3
                }
            }
            EditorInfo.IME_ACTION_DONE -> enterState = 4
        }
        mToggleStates.mStateEnter = enterState
        if (newInputMode != mInputMode && MODE_UNSET != newInputMode) {
            saveInputMode(newInputMode)
            KeyboardManager.instance.switchKeyboard()
        }
    }

    val isNumberSkb: Boolean
        /**
         * 鏄惁鏄暟瀛楃洏杈撳叆娉曟ā寮?
         */
        get() = mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_NUMBER

    val isTextEditSkb: Boolean
        get() = mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_TEXTEDIT

    val isChinese: Boolean
        /**
         * 鏄惁鏄腑鏂囪瑷€銆?
         */
        get() = mInputMode and MASK_LANGUAGE == MASK_LANGUAGE_CN
    val isChineseT9: Boolean
        /**
         * 鏄惁鐨?瀹牸涓枃璇█
         */
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE) == MODE_T9_CHINESE

    val isQwert: Boolean
        /**
         * 鏄惁鐨勫叏閿腑鏂囪瑷€
         */
        get() = mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_QWERTY_PINYIN || mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_QWERTY_ABC

    val isChineseHandWriting: Boolean
        /**
         * 鎵嬪啓涓枃璇█
         */
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE) == MODE_HANDWRITING_CHINESE
    val isEnglish: Boolean
        /**
         * 鏄惁鏄蒋浠剁洏鑻辫妯″紡
         */
        get() = mInputMode and MASK_LANGUAGE == MASK_LANGUAGE_EN
    val isEnglishLower: Boolean
        /**
         * 鏄惁鏄蒋閿洏楂橈紙灏忓啓锛夋ā寮?
         */
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE or MASK_CASE) == MODE_SKB_ENGLISH_LOWER
    val isEnglishUpperCase: Boolean
        /**
         * 鏄惁鏄蒋閿洏楂橈紙澶у啓锛夋ā寮?
         */
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE or MASK_CASE) == MODE_SKB_ENGLISH_UPPER


    val isEnglishUpperLockCase: Boolean
        /**
         * 鏄惁鏄蒋閿洏楂橈紙閿佸畾澶у啓锛夋ā寮?
         */
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE or MASK_CASE) == MODE_SKB_ENGLISH_UPPER_LOCK

    /**
     * 淇濆瓨鏂扮殑杈撳叆娉曟ā寮?
     */
    fun saveInputMode(newInputMode: Int) {
        mInputMode = newInputMode // 璁剧疆鏂扮殑杈撳叆娉曟ā寮忎负褰撳墠鐨勮緭鍏ユ硶妯″紡
        // 璇█閿細鏄剧ず涓枃鎴栬€呰嫳鏂囥€佷腑绗︺€佽嫳绗︾殑閿?
        if (isEnglish) {
            val charCase = mInputMode and MASK_CASE
            mToggleStates.charCase = charCase
            Kernel.initImeSchema(CustomConstant.SCHEMA_EN)
        } else {
            Kernel.initImeSchema(getInstance().internal.pinyinModeRime.getValue())
        }
        if (isChinese || isEnglish) {
            mRecentLauageInputMode = mInputMode
            getInstance().internal.inputDefaultMode.setValue(mInputMode)
        }
    }

    /**
     * 閲嶇疆杈撳叆娉曟ā寮?
     */
    fun reset( ) {
        mInputMode = MODE_UNSET
        mRecentLauageInputMode = MODE_UNSET
    }


}


package com.paopaoime.aikeyboard.ime.keyboard

import android.view.KeyEvent
import com.paopaoime.aikeyboard.ime.manager.InputModeSwitcherManager
import com.paopaoime.aikeyboard.ime.prefs.behavior.SkbStyleMode

object KeyboardData {

    val layoutQwertyCn: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            arrayOf(
                KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
            ),
            arrayOf(
                KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
                KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
            ),
            arrayOf(
                KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_DEL,
            ),
        ),
        SkbStyleMode.Samsung to arrayOf(
            arrayOf(
                KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
            ),
            arrayOf(
                KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
                KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
            ),
            arrayOf(
                KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_DEL,
            ),
        ),
    )

    val layoutT9Cn: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            arrayOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3),
            arrayOf(KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6),
            arrayOf(KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_DEL),
        ),
        SkbStyleMode.Samsung to arrayOf(
            arrayOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3),
            arrayOf(KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6),
            arrayOf(KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_DEL),
        ),
    )

    val layoutHandwritingCn: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_LEFT_SYMBOL_12),
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_EMOJI_4),
        ),
        SkbStyleMode.Samsung to arrayOf(
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_LEFT_SYMBOL_12),
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_EMOJI_4),
        ),
    )

    val layoutQwertyEn: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            layoutQwertyCn.getValue(SkbStyleMode.Google)[0],
            layoutQwertyCn.getValue(SkbStyleMode.Google)[1],
            arrayOf(
                KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_DEL,
            ),
        ),
        SkbStyleMode.Samsung to arrayOf(
            layoutQwertyCn.getValue(SkbStyleMode.Samsung)[0],
            layoutQwertyCn.getValue(SkbStyleMode.Samsung)[1],
            arrayOf(
                KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_DEL,
            ),
        ),
    )

    val layoutT9Number: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            arrayOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3),
            arrayOf(KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6),
            arrayOf(KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0),
        ),
        SkbStyleMode.Samsung to arrayOf(
            arrayOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3),
            arrayOf(KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6),
            arrayOf(KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0),
        ),
    )

    val layoutLX17CnWithLeftPrefix: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_LEFT_SYMBOL_12, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T),
            arrayOf(KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A),
            arrayOf(KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J),
        ),
        SkbStyleMode.Samsung to arrayOf(
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_LEFT_SYMBOL_12, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T),
            arrayOf(KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A),
            arrayOf(KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J),
        ),
    )

    val layoutLX17Cn: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            arrayOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y),
            arrayOf(KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S),
            arrayOf(KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K),
        ),
        SkbStyleMode.Samsung to arrayOf(
            arrayOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y),
            arrayOf(KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S),
            arrayOf(KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K),
        ),
    )

    val layoutStrokeCn: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to layoutT9Cn.getValue(SkbStyleMode.Google),
        SkbStyleMode.Samsung to layoutT9Cn.getValue(SkbStyleMode.Samsung),
    )

    val layoutTextEdit: Map<SkbStyleMode, Array<Array<Int>>> = mapOf(
        SkbStyleMode.Google to arrayOf(
            arrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT),
            arrayOf(KeyEvent.KEYCODE_DPAD_DOWN, InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_ALL, InputModeSwitcherManager.USER_DEF_KEYCODE_COPY),
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_CUT, InputModeSwitcherManager.USER_DEF_KEYCODE_PASTE, InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_MODE),
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_MOVE_START, InputModeSwitcherManager.USER_DEF_KEYCODE_MOVE_END, KeyEvent.KEYCODE_DEL),
        ),
        SkbStyleMode.Samsung to arrayOf(
            arrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT),
            arrayOf(KeyEvent.KEYCODE_DPAD_DOWN, InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_ALL, InputModeSwitcherManager.USER_DEF_KEYCODE_COPY),
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_CUT, InputModeSwitcherManager.USER_DEF_KEYCODE_PASTE, InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_MODE),
            arrayOf(InputModeSwitcherManager.USER_DEF_KEYCODE_MOVE_START, InputModeSwitcherManager.USER_DEF_KEYCODE_MOVE_END, KeyEvent.KEYCODE_DEL),
        ),
    )
}

package com.paopaoime.aikeyboard.ime.utils.thread

import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ThreadPoolUtils private constructor() {

    companion object {
        /**
         * 默认线程池
         */
        private var DEFAULT_EXECUTOR: ThreadPoolExecutor? = null

        /**
         * 单线程线程池
         */
        private var SINGLETON_EXECUTOR: ThreadPoolExecutor? = null

        /**
         * 定时任务线程池
         */
        private var SCHEDULED_EXECUTOR: ScheduledThreadPoolExecutor? = null

        /**
         * 定时任务线程池(创建守护线程执行)
         */
        private var SCHEDULED_DAEMON_EXECUTOR: ScheduledThreadPoolExecutor? = null

        init {
            val cpuCount = Runtime.getRuntime().availableProcessors()
            val corePoolSize = max((cpuCount * 2).toDouble(), 4.0).toInt()
            DEFAULT_EXECUTOR = ThreadPoolExecutor(
                corePoolSize, corePoolSize * 2, 30, TimeUnit.SECONDS, LinkedBlockingQueue(),
                NamingThreadFactory("ThreadPoolUtils")
            ) { r, executor ->
                try {
                    executor.queue.put(r)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            SCHEDULED_EXECUTOR = ScheduledThreadPoolExecutor(
                corePoolSize,
                NamingThreadFactory("ThreadPoolUtils-scheduled", false)
            )
            SCHEDULED_DAEMON_EXECUTOR = ScheduledThreadPoolExecutor(
                corePoolSize,
                NamingThreadFactory("ThreadPoolUtils-scheduled-daemon", true)
            )
            SINGLETON_EXECUTOR = newSingletonExecutor("ThreadPoolUtils-singleton")
        }

        fun execute(runnable: Runnable?) {
            DEFAULT_EXECUTOR!!.execute(runnable)
        }

        fun executeSingleton(runnable: Runnable?) {
            SINGLETON_EXECUTOR!!.execute(runnable)
        }

        /**
         * 调用线程池执行定时任务
         *
         * @param runnable 任务
         * @param delay    启动延迟
         * @param unit     启动延迟的时间单位
         */
        fun schedule(runnable: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
            return SCHEDULED_EXECUTOR!!.schedule(runnable, delay, unit)
        }

        /**
         * 调用线程池执行定时的周期任务
         *
         * @param runnable     任务
         * @param initialDelay 启动延迟, millseconds
         * @param period       执行周期
         * @param unit         执行周期时间单位
         */
        fun scheduleAtFixedRate(
            runnable: Runnable?,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*> {
            return SCHEDULED_EXECUTOR!!.scheduleAtFixedRate(runnable, initialDelay, period, unit)
        }

        /**
         * 调用线程池执行定时任务(在守护线程中执行)
         *
         * @param runnable 任务
         * @param delay    启动延迟
         * @param unit     启动延迟的时间单位
         */
        fun scheduleDaemon(runnable: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
            return SCHEDULED_DAEMON_EXECUTOR!!.schedule(runnable, delay, unit)
        }

        /**
         * 璋冪敤绾跨▼姹犳墽琛屽畾鏃剁殑鍛ㄦ湡浠诲姟 (鍦ㄥ畧鎶ょ嚎绋嬩腑鎵ц)
         *
         * @param runnable     浠诲姟
         * @param initialDelay 鍚姩寤惰繜, millseconds
         * @param period       鎵ц鍛ㄦ湡
         * @param unit         鎵ц鍛ㄦ湡鏃堕棿鍗曚綅
         */
        fun scheduleAtFixedRateInDaemon(
            runnable: Runnable?,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit?
        ): ScheduledFuture<*> {
            return SCHEDULED_DAEMON_EXECUTOR!!.scheduleAtFixedRate(
                runnable,
                initialDelay,
                period,
                unit
            )
        }

        /**
         * 鍒涘缓涓€涓崟绾跨▼绾跨▼姹?
         *
         * @param threadPoolName 绾跨▼姹犲悕绉?
         * @return 鏂板垱寤虹殑鍗曠嚎绋嬬嚎绋嬫睜
         */
        fun newSingletonExecutor(threadPoolName: String?): ThreadPoolExecutor {
            return ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
                NamingThreadFactory(threadPoolName!!)
            )
        }

        /**
         * 鍒涘缓涓€涓畾鏃朵换鍔＄▼绾跨▼姹?
         *
         * @param threadPoolName 绾跨▼姹犲悕绉?
         * @param daemon         鏄惁涓哄畧鎶ょ嚎绋?
         * @return 鏂板垱寤虹殑瀹氭椂浠诲姟绾跨▼姹?
         */
        fun newScheduledExecutor(
            threadPoolName: String?,
            daemon: Boolean
        ): ScheduledThreadPoolExecutor {
            return ScheduledThreadPoolExecutor(
                1, NamingThreadFactory(
                    threadPoolName!!, daemon
                )
            )
        }

        /**
         * 鍙栨秷浠诲姟
         *
         * @param future 寰呭彇娑堜换鍔?
         */
        fun cancel(future: Future<*>) {
            future.cancel(true)
        }
    }
}


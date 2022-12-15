package net.postchain.network.mastersub.master

import net.postchain.core.Shutdownable
import java.util.*
import kotlin.concurrent.schedule

interface MasterCommunicationManager : Shutdownable {
    fun init()
}

/**
 * Implements [Timer] object common for all instances.
 */
abstract class AbstractMasterCommunicationManager : MasterCommunicationManager {

    companion object {
        private val timer: Timer by lazy {
            Timer("MasterCommunicationManagerTimer", true)
        }

        /**
         * Schedules a [task] to be executed periodically.
         */
        @JvmStatic
        protected fun scheduleTask(period: Long, task: TimerTask.() -> Unit): TimerTask {
            return timer.schedule(0L, period, task)
        }
    }

}
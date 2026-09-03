package com.exynosbess.app

import com.exynosbess.app.root.RootEnvironment

object RootManager {

    data class RootResult(

        val granted: Boolean,

        val output: String,

        val provider: String =
            "Desconhecido",

        val version: String? =
            null,

        val suPath: String? =
            null,

        val selinux: String? =
            null
    )


    fun checkRoot():
        RootResult {

        val environment =
            RootEnvironment.detect()


        return RootResult(

            granted =
                environment.available,

            output =
                environment.uidOutput,

            provider =
                environment
                    .provider
                    .displayName,

            version =
                environment.version,

            suPath =
                environment.suPath,

            selinux =
                environment.selinux
        )
    }


    fun getEnvironment():
        RootEnvironment.Info {

        return RootEnvironment.detect()
    }
}

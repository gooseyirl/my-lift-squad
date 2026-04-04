package com.gooseco.myliftsquad

import android.app.Application
import com.gooseco.myliftsquad.data.db.AppDatabase

class MyLiftSquadApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}

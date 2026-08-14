package com.linkvault.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * No DI framework in this project (see LinkVaultApplication), so ViewModels
 * with constructor dependencies need a manual ViewModelProvider.Factory.
 * This wraps that boilerplate behind a plain creation lambda.
 */
fun <T : ViewModel> viewModelFactory(creator: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }

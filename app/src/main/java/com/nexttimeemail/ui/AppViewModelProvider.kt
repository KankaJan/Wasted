package com.nexttimeemail.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexttimeemail.NextTimeEmailApp
import com.nexttimeemail.ui.history.HistoryViewModel
import com.nexttimeemail.ui.roster.RosterViewModel

/** Builds every view model from the app's single repository. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { RosterViewModel(app().repository, app().settings) }
        initializer { HistoryViewModel(app().repository) }
    }
}

private fun androidx.lifecycle.viewmodel.CreationExtras.app(): NextTimeEmailApp =
    this[APPLICATION_KEY] as NextTimeEmailApp

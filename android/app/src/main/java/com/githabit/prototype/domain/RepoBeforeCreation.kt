package com.githabit.prototype.domain

import java.time.LocalDate

/** Structured date argument; stable English exception remains available to pure domain callers. */
class RepoBeforeCreation(val date: LocalDate) : IllegalArgumentException(
    "This repo did not exist on $date. Check your device date.")

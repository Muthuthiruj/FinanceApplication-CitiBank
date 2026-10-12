package com.example.financeapplication.domain.repository

import com.example.financeapplication.domain.model.StatementData
import java.io.File

interface StatementExporter {
    /** Renders the statement to a PDF file and returns it. */
    suspend fun export(data: StatementData): File
}

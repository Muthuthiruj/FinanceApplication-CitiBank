package com.example.financeapplication.di

import com.example.financeapplication.data.pdf.PdfStatementExporter
import com.example.financeapplication.data.repository.*
import com.example.financeapplication.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun bank(impl: BankRepositoryImpl): BankRepository
    @Binds @Singleton abstract fun card(impl: CardRepositoryImpl): CardRepository
    @Binds @Singleton abstract fun rewards(impl: RewardsRepositoryImpl): RewardsRepository
    @Binds @Singleton abstract fun loans(impl: LoanRepositoryImpl): LoanRepository
    @Binds @Singleton abstract fun notifications(impl: NotificationRepositoryImpl): NotificationRepository
    @Binds @Singleton abstract fun support(impl: SupportRepositoryImpl): SupportRepository
    @Binds @Singleton abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds @Singleton abstract fun statementExporter(impl: PdfStatementExporter): StatementExporter
}

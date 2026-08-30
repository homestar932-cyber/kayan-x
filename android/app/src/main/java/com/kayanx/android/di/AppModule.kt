package com.kayanx.android.di

import android.content.Context
import com.kayanx.android.agent.executor.ToolExecutor
import com.kayanx.android.agent.loop.AgentOrchestrator
import com.kayanx.android.agent.planner.LocalLlamaPlanner
import com.kayanx.android.agent.planner.MockPlanner
import com.kayanx.android.agent.planner.Planner
import com.kayanx.android.agent.verifier.DeterministicVerifier
import com.kayanx.android.fs.FileBridge
import com.kayanx.android.fs.policy.FilePolicy
import com.kayanx.android.fs.saf.PersistedTreeStore
import com.kayanx.android.native.LlamaEngine
import com.kayanx.android.native.ModelLoader
import com.kayanx.android.settings.EngineSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideTreeStore(@ApplicationContext ctx: Context) = PersistedTreeStore(ctx)

    @Provides @Singleton
    fun providePolicy() = FilePolicy()

    @Provides @Singleton
    fun provideFileBridge(
        @ApplicationContext ctx: Context,
        treeStore: PersistedTreeStore,
        policy: FilePolicy
    ) = FileBridge(ctx, treeStore, policy)

    @Provides @Singleton
    fun provideToolExecutor(bridge: FileBridge) = ToolExecutor(bridge)

    @Provides @Singleton
    fun provideVerifier(bridge: FileBridge) = DeterministicVerifier(bridge)

    @Provides @Singleton
    fun provideEngine() = LlamaEngine()

    @Provides @Singleton
    fun provideModelLoader(@ApplicationContext ctx: Context, engine: LlamaEngine) =
        ModelLoader(ctx, engine)

    @Provides @Singleton
    fun provideEngineSettings(@ApplicationContext ctx: Context) = EngineSettings(ctx)

    @Provides @Singleton
    fun providePlanner(engine: LlamaEngine): Planner = LocalLlamaPlanner(engine)

    @Provides @Singleton @Named("mock")
    fun provideMockPlanner(): Planner = MockPlanner()

    @Provides @Singleton
    fun provideOrchestrator(
        planner: Planner,
        executor: ToolExecutor,
        verifier: DeterministicVerifier
    ) = AgentOrchestrator(planner, executor, verifier)
}

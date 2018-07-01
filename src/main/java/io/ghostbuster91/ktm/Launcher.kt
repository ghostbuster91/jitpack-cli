package io.ghostbuster91.ktm

import com.github.ajalt.clikt.core.NoRunCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.ghostbuster91.ktm.commands.*
import io.ghostbuster91.ktm.components.*
import io.ghostbuster91.ktm.identifier.IdentifierResolver
import io.ghostbuster91.ktm.identifier.artifact.*
import io.ghostbuster91.ktm.identifier.version.DefaultVersionResolver
import io.ghostbuster91.ktm.identifier.version.LatestVersionFetchingIdentifierResolver
import io.ghostbuster91.ktm.identifier.version.SimpleVersionResolver
import io.ghostbuster91.ktm.utils.NullPrintStream
import io.reactivex.Observable
import jline.internal.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

typealias GetHomeDir = () -> File

var logger: Logger = LineWrappingLogger()
private val retrofit = Retrofit.Builder()
        .client(OkHttpClient.Builder().readTimeout(5, TimeUnit.MINUTES).build())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
        .baseUrl("https://jitpack.io/")
        .build()
private val jitPackApi = retrofit.create(JitPackApi::class.java)
private val buildApi = retrofit.create(BuildLogApi::class.java)
private val directoryManager = KtmDirectoryManager({ File(System.getProperty("user.home")) })
private val aliasRepository = AliasFileRepository(directoryManager)
private val identifierSolver = IdentifierResolver(
        artifactResolvers = listOf(AliasArtifactResolver(aliasRepository), SearchingArtifactResolver({
            { jitPackApi.search(it).blockingFirst() }.withWaiter()
        }), SimpleArtifactResolver()),
        versionResolvers = listOf(SimpleVersionResolver(), LatestVersionFetchingIdentifierResolver(
                { g, a -> { jitPackApi.latestRelease(g, a).blockingFirst() }.withWaiter() }), DefaultVersionResolver()))
private val jitPackArtifactToLinkTranslator = JitPackArtifactToLinkTranslator({ g, a, v ->
    { buildApi.getBuildLog(g, a, v).blockingFirst() }.withWaiter()
})
private val tarFileDownloader = TarFileDownloader(createWaitingIndicator())

fun main(args: Array<String>) {
    Log.setOutput(NullPrintStream())
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")
    KTM().subcommands(
            Install(directoryManager, jitPackArtifactToLinkTranslator, tarFileDownloader, identifierSolver),
            Aliases(aliasRepository),
            Info(),
            Search(),
            Details(identifierSolver),
            Use(directoryManager, identifierSolver),
            io.ghostbuster91.ktm.commands.List(directoryManager)
    ).main(args)
}

private class KTM : NoRunCliktCommand() {
    init {
        versionOption(Build.getVersion())
    }
}

private fun <T> (() -> T).withWaiter(): T {
    val waiter = createWaitingIndicator().subscribe()
    return invoke().also { waiter.dispose() }
}

private fun createWaitingIndicator(): Observable<out Any> = Observable.interval(100, TimeUnit.MILLISECONDS)
        .doOnNext { logger.append(".") }
        .doOnDispose { logger.info("") }

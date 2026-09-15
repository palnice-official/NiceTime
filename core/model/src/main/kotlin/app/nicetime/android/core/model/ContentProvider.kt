package app.nicetime.android.core.model

/**
 * Contract every content source implements. Conceptual port of the Rust
 * provider contract; capabilities replace assumptions: a call against a
 * provider that does not declare the capability returns Unsupported.
 */
interface ContentProvider {
    val instanceId: ProviderInstanceId
    val capabilities: ProviderCapabilities

    suspend fun catalog(request: CatalogRequest): ProviderResult<Page<MediaSummary>>
    suspend fun search(request: SearchRequest): ProviderResult<Page<MediaSummary>>
    suspend fun details(ref: MediaRef): ProviderResult<MediaDetails>
    suspend fun streams(ref: EpisodeRef): ProviderResult<List<Release>>

    /** For movies, pass an EpisodeRef whose media.type is MOVIE and ids are the movie's. */
    suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose): ProviderResult<ResolvedStream>
}

/** Deterministic in-memory provider used by contract tests and fixture UI. */
class FakeProvider(
    override val instanceId: ProviderInstanceId = ProviderInstanceId.new(),
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        catalog = true,
        search = true,
        streamResolution = true,
        downloadable = true,
        supportedTypes = setOf(MediaType.MOVIE, MediaType.SERIES),
    ),
    private val catalogPages: List<List<MediaSummary>> = emptyList(),
    private val searchable: (String) -> List<MediaSummary> = { emptyList() },
    private val detailsByRef: Map<String, MediaDetails> = emptyMap(),
    private val releasesByEpisode: Map<String, List<Release>> = emptyMap(),
    private val resolver: (ReleaseRef, ResolvePurpose) -> ResolvedStream? = { _, _ -> null },
) : ContentProvider {

    override suspend fun catalog(request: CatalogRequest): ProviderResult<Page<MediaSummary>> {
        if (!capabilities.catalog) return ProviderResult.Failure(ProviderFailure.Unsupported("catalog"))
        return catalogPages.getOrNull(request.page - 1)
            ?.let { ProviderResult.Ok(Page(it, nextCursor = if (request.page < catalogPages.size) "p${request.page + 1}" else null)) }
            ?: ProviderResult.Ok(Page(emptyList()))
    }

    override suspend fun search(request: SearchRequest): ProviderResult<Page<MediaSummary>> {
        if (!capabilities.search) return ProviderResult.Failure(ProviderFailure.Unsupported("search"))
        return ProviderResult.Ok(Page(searchable(request.query)))
    }

    override suspend fun details(ref: MediaRef): ProviderResult<MediaDetails> =
        detailsByRef["${ref.provider}:${ref.providerMediaId}"]
            ?.let { ProviderResult.Ok(it) }
            ?: ProviderResult.Failure(ProviderFailure.NotFound())

    override suspend fun streams(ref: EpisodeRef): ProviderResult<List<Release>> {
        if (!capabilities.streamResolution) return ProviderResult.Failure(ProviderFailure.Unsupported("stream resolution"))
        val key = "${ref.media.provider}:${ref.media.providerMediaId}:${ref.providerEpisodeId}"
        return ProviderResult.Ok(releasesByEpisode[key] ?: emptyList())
    }

    override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose): ProviderResult<ResolvedStream> {
        if (purpose == ResolvePurpose.DOWNLOAD && !capabilities.downloadable) {
            return ProviderResult.Failure(ProviderFailure.Unsupported("download"))
        }
        return resolver(ref, purpose)
            ?.let { ProviderResult.Ok(it) }
            ?: ProviderResult.Failure(ProviderFailure.NotFound("No stream for release ${ref.providerReleaseId}"))
    }
}

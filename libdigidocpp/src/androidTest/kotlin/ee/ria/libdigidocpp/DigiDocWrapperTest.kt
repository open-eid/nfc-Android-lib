package ee.ria.libdigidocpp

import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.File
import kotlin.test.assertFails
import kotlin.test.assertTrue

class DigiDocWrapperTest {

    private lateinit var wrapper: DigiDocWrapper

    @Before
    fun setup() {
        wrapper = DigiDocWrapperImpl(InstrumentationRegistry.getInstrumentation().targetContext)
        if (wrapper.isInitialized().not()) {
            wrapper.initialize()
        }
    }

    private fun expectError(
        expectedError: ErrorCode,
        actionFunc: () -> Unit
    ) {
        val error = assertFails(actionFunc)
        val isExpectedError = error is DomainException && error.code == expectedError
        assertTrue(isExpectedError, "Expected $expectedError, but was ${(error as? DomainException)?.code}")
    }

    private fun writeToCache(fileName: String, @RawRes containerResId: Int): String {
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val filePath = context.cacheDir.absolutePath + File.separator + fileName
            File(filePath).outputStream().use { output ->
                context.resources.openRawResource(containerResId).use { input ->
                    input.copyTo(output)
                }
            }

            return filePath
        } catch (ex: Exception) {
            Timber.e(ex, "writeToCache(): failed to write file($fileName) to cache")
            throw ex
        }
    }

    private fun deleteFromCache(path: String) {
        try {
            File(path).delete()
        } catch (secEx: SecurityException) {
            Timber.e(secEx, "deleteFromCache(): failed to remove file at $path")
        }
    }

    private fun executeErrorTest(
        fileName: String,
        @RawRes containerResId: Int,
        expectedError: ErrorCode
    ) {
        val filePath = writeToCache(fileName, containerResId)
        expectError(expectedError) {
            val container = wrapper.openContainer(filePath)
            wrapper.isValid(container)
        }
        deleteFromCache(filePath)
    }

    @Test
    fun whenEmptyContainerIsUsed_thenContainerOpeningErrorIsThrown() {
        executeErrorTest(
            fileName = "empty_container.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.empty_container,
            expectedError = ErrorCode.DIGIDOC_OPEN_CONTAINER_ERROR
        )
    }

    @Test
    fun whenMimeTypeFileIsNotFirst_thenContainerOpeningErrorIsThrown() {
        executeErrorTest(
            fileName = "order.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.order,
            expectedError = ErrorCode.DIGIDOC_OPEN_CONTAINER_ERROR
        )
    }

    @Test
    fun whenMimeTypeIsInvalid_thenContainerOpeningErrorIsThrown() {
        executeErrorTest(
            fileName = "invalid_mimetype.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.invalid_mimetype,
            expectedError = ErrorCode.DIGIDOC_OPEN_CONTAINER_ERROR
        )
    }

    @Test
    fun whenManifestFileIsMissing_thenContainerOpeningErrorIsThrown() {
        executeErrorTest(
            fileName = "no_manifest.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.no_manifest,
            expectedError = ErrorCode.DIGIDOC_OPEN_CONTAINER_ERROR
        )
    }

    @Test
    fun whenManifestRootTypeIsInvalid_thenContainerOpeningErrorIsThrown() {
        executeErrorTest(
            fileName = "invalid_root_type.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.invalid_root_type,
            expectedError = ErrorCode.DIGIDOC_OPEN_CONTAINER_ERROR
        )
    }

    @Test
    fun whenExtraFileIsPresent_thenContainerOpeningErrorIsThrown() {
        executeErrorTest(
            fileName = "extra_file.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.extra_file,
            expectedError = ErrorCode.DIGIDOC_OPEN_CONTAINER_ERROR
        )
    }

    @Test
    fun whenFileListedInManifestIsNotPresent_thenContainerOpeningErrorIsThrown() {
        executeErrorTest(
            fileName = "missing_file.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.missing_file,
            expectedError = ErrorCode.DIGIDOC_OPEN_CONTAINER_ERROR
        )
    }

    @Test
    fun whenSignaturesFileIsMissingFromMetaInf_thenNoValidSignaturesErrorIsThrown() {
        executeErrorTest(
            fileName = "no_signature.asice",
            containerResId = ee.ria.libdigidocpp.test.R.raw.no_signature,
            expectedError = ErrorCode.DIGIDOC_VALIDATE_SIGNATURES_NO_VALID_SIGNATURES
        )
    }

    /*
    This test fails currently because Digidoc fails to download the TLS list at https://ec.europa.eu/tools/lotl/eu-lotl.xml for some reason.
    Once we figure out why it fails and manage to fix it for the test then we can re-enanble the test.
     */
    /*
    @Test
    fun whenValidContainerIsUsed_thenNoErrorsAreThrown() {
        val filePath = writeToCache("trust_modded.asice", ee.ria.libdigidocpp.test.R.raw.trust_modded)
        val container = wrapper.openContainer(filePath)
        wrapper.isValid(container)
        deleteFromCache(filePath)
    }
     */
}
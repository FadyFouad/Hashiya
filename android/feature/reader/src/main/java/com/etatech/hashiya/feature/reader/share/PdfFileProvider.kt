package com.etatech.hashiya.feature.reader.share

import androidx.core.content.FileProvider

/**
 * The reader's FileProvider (`${applicationId}.pdfs`, res/xml/pdf_paths.xml). Its own class, because the merged manifest keys
 * providers by class name and feature:library already declares androidx.core's FileProvider for its exports.
 */
class PdfFileProvider : FileProvider()

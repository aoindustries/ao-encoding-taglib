/*
 * ao-encoding-taglib - High performance streaming character encoding in a JSP environment.
 * Copyright (C) 2009, 2010, 2011, 2012, 2013, 2016, 2017, 2019, 2020, 2021  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-encoding-taglib.
 *
 * ao-encoding-taglib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-encoding-taglib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-encoding-taglib.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoapps.encoding.taglib;

import com.aoapps.encoding.EncodingContext;
import com.aoapps.encoding.MediaEncoder;
import com.aoapps.encoding.MediaType;
import com.aoapps.encoding.MediaValidator;
import com.aoapps.encoding.MediaWriter;
import com.aoapps.encoding.servlet.EncodingContextEE;
import com.aoapps.io.buffer.AutoTempFileWriter;
import com.aoapps.io.buffer.BufferResult;
import com.aoapps.io.buffer.BufferWriter;
import com.aoapps.io.buffer.CharArrayBufferWriter;
import com.aoapps.io.buffer.EmptyResult;
import com.aoapps.tempfiles.TempFileContext;
import com.aoapps.tempfiles.servlet.TempFileContextEE;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.SimpleTagSupport;

/**
 * <p>
 * The exhibits all of the behavior of {@link EncodingFilteredTag} with
 * the only exception being that it buffers its content instead of using filters.
 * This allows the tag to capture its body.  Character validation is performed
 * as the data goes into the buffer to ensure the captured data is correct for
 * its content type.
 * </p>
 * <p>
 * The tag also has the addition of a separate output type.  Thus, we have three
 * types involved:
 * </p>
 * <ol>
 * <li>contentType - The characters are validated to this type as they go into the buffer.</li>
 * <li>outputType - Our output characters are validated to this type as they are written.</li>
 * <li>containerType - Our output characters are encoded to this type as they are written.</li>
 * </ol>
 *
 * @author  AO Industries, Inc.
 */
public abstract class EncodingBufferedTag extends SimpleTagSupport {

	private static final Logger logger = Logger.getLogger(EncodingBufferedTag.class.getName());

	/**
	 * Creates an instance of the currently preferred {@link BufferWriter}.
	 * Buffering strategies may change over time as technology develops and
	 * options become available.
	 *
	 * @see  TempFileContext
	 * @see  AutoTempFileWriter
	 */
	public static BufferWriter newBufferWriter(TempFileContext tempFileContext, long tempFileThreshold) {
		//return new SegmentedWriter();
		BufferWriter bufferWriter = new CharArrayBufferWriter();
		if(tempFileThreshold != Long.MAX_VALUE) {
			bufferWriter = new AutoTempFileWriter(
				bufferWriter,
				tempFileContext,
				tempFileThreshold
			);
		}
		return bufferWriter;
	}

	/**
	 * @see  #newBufferWriter(com.aoapps.tempfiles.TempFileContext, long)
	 * @see  AutoTempFileWriter#DEFAULT_TEMP_FILE_THRESHOLD
	 */
	public static BufferWriter newBufferWriter(TempFileContext tempFileContext) {
		return newBufferWriter(tempFileContext, AutoTempFileWriter.DEFAULT_TEMP_FILE_THRESHOLD);
	}

	/**
	 * @see  #newBufferWriter(com.aoapps.tempfiles.TempFileContext, long)
	 * @see  TempFileContextEE#get(javax.servlet.ServletRequest)
	 */
	public static BufferWriter newBufferWriter(ServletRequest request, long tempFileThreshold) {
		return newBufferWriter(TempFileContextEE.get(request), tempFileThreshold);
	}

	/**
	 * @see  #newBufferWriter(javax.servlet.ServletRequest, long)
	 * @see  AutoTempFileWriter#DEFAULT_TEMP_FILE_THRESHOLD
	 */
	public static BufferWriter newBufferWriter(ServletRequest request) {
		return newBufferWriter(request, AutoTempFileWriter.DEFAULT_TEMP_FILE_THRESHOLD);
	}

	/**
	 * Gets the type of data that is contained by this tag.
	 */
	public abstract MediaType getContentType();

	/**
	 * Gets the output type of this tag.  This is used to determine the correct
	 * encoder.  If the tag never has any output this should return {@code null}.
	 * When {@code null} is returned, any output will result in an error.
	 */
	public abstract MediaType getOutputType();

	/**
	 * Gets the number of characters that may be buffered before switching to the
	 * use of a temp file.
	 *
	 * @return the threshold or {@link Long#MAX_VALUE} to never use temp files.
	 *
	 * @see  AutoTempFileWriter#DEFAULT_TEMP_FILE_THRESHOLD
	 */
	public long getTempFileThreshold() {
		return AutoTempFileWriter.DEFAULT_TEMP_FILE_THRESHOLD;
	}

	/**
	 * @deprecated  You should probably be implementing in {@link #doTag(com.aoapps.io.buffer.BufferResult, java.io.Writer)}
	 *
	 * @see  #doTag(com.aoapps.io.buffer.BufferResult, java.io.Writer)
	 */
	@Deprecated
	@Override
	public void doTag() throws JspException, IOException {
		final PageContext pageContext = (PageContext)getJspContext();
		final HttpServletRequest request = (HttpServletRequest)pageContext.getRequest();
		final RequestEncodingContext parentEncodingContext = RequestEncodingContext.getCurrentContext(request);
		// The output type cannot be determined until the body of the tag is invoked, because nested tags may
		// alter the resulting type.  We invoke the body first to accommodate nested tags.

		final BufferResult capturedBody;
		JspFragment body = getJspBody();
		if(body != null) {
			// Capture the body output while validating
			BufferWriter captureBuffer = newBufferWriter(request, getTempFileThreshold());
			try {
				final MediaType myContentType = getContentType();
				MediaValidator captureValidator = MediaValidator.getMediaValidator(myContentType, captureBuffer);
				RequestEncodingContext.setCurrentContext(
					request,
					new RequestEncodingContext(myContentType, captureValidator)
				);
				try {
					invoke(body, captureValidator);
					captureValidator.flush();
				} finally {
					// Restore previous encoding context that is used for our output
					RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
				}
			} finally {
				captureBuffer.close();
			}
			capturedBody = captureBuffer.getResult();
		} else {
			capturedBody = EmptyResult.getInstance();
		}

		MediaType newOutputType = getOutputType();
		if(newOutputType == null) {
			// No output, error if anything written.
			// prefix skipped
			doTag(capturedBody, FailOnWriteWriter.getInstance());
			// suffix skipped
		} else {
			final HttpServletResponse response = (HttpServletResponse)pageContext.getResponse();
			final JspWriter out = pageContext.getOut();

			// Determine the container's content type and validator
			final MediaType containerType;
			final Writer containerValidator;
			if(parentEncodingContext != null) {
				// Use the output type of the parent
				containerType = parentEncodingContext.contentType;
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("containerType from parentEncodingContext: " + containerType);
				}
				assert parentEncodingContext.validMediaInput.isValidatingMediaInputType(containerType)
					: "It is a bug in the parent to not validate its input consistent with its content type";
				// Already validated
				containerValidator = out;
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("containerValidator from parentEncodingContext: " + containerValidator);
				}
			} else {
				// Use the content type of the response
				String responseContentType = response.getContentType();
				// Default to XHTML: TODO: Is there a better way since can't set content type early in response then reset again...
				if(responseContentType == null) responseContentType = MediaType.XHTML.getContentType();
				containerType = MediaType.getMediaTypeForContentType(responseContentType);
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("containerType from responseContentType: " + containerType + " from " + responseContentType);
				}
				// Need to add validator
				// TODO: Only validate when in development mode for performance?
				containerValidator = MediaValidator.getMediaValidator(containerType, out);
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("containerValidator from containerType: " + containerValidator + " from " + containerType);
				}
			}

			// Write any prefix
			writePrefix(containerType, containerValidator);

			// Find the encoder
			EncodingContext encodingContext = new EncodingContextEE(pageContext.getServletContext(), request, response);
			MediaEncoder mediaEncoder = MediaEncoder.getInstance(encodingContext, newOutputType, containerType);
			if(mediaEncoder != null) {
				if(logger.isLoggable(Level.FINER)) {
					logger.finer("Using MediaEncoder: " + mediaEncoder);
				}
				logger.finest("Setting encoder options");
				setMediaEncoderOptions(mediaEncoder);
				// Encode our output.  The encoder guarantees valid output for our parent.
				logger.finest("Writing encoder prefix");
				writeEncoderPrefix(mediaEncoder, out); // TODO: Skip prefix and suffix when empty?  Pass capturedBody so implementation may decide?
				try {
					MediaWriter mediaWriter = new MediaWriter(encodingContext, mediaEncoder, out);
					RequestEncodingContext.setCurrentContext(
						request,
						new RequestEncodingContext(newOutputType, mediaWriter)
					);
					try {
						doTag(capturedBody, mediaWriter);
					} finally {
						// Restore previous encoding context that is used for our output
						RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
					}
				} finally {
					logger.finest("Writing encoder suffix");
					writeEncoderSuffix(mediaEncoder, out);
				}
			} else {
				// If parentValidMediaInput exists and is validating our output type, no additional validation is required
				if(
					parentEncodingContext != null
					&& parentEncodingContext.validMediaInput.isValidatingMediaInputType(newOutputType)
				) {
					if(logger.isLoggable(Level.FINER)) {
						logger.finer("Passing-through with validating parent: " + parentEncodingContext.validMediaInput);
					}
					RequestEncodingContext.setCurrentContext(
						request,
						new RequestEncodingContext(newOutputType, parentEncodingContext.validMediaInput)
					);
					try {
						doTag(capturedBody, out);
					} finally {
						RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
					}
				} else {
					// Not using an encoder and parent doesn't validate our output, validate our own output.
					MediaValidator validator = MediaValidator.getMediaValidator(newOutputType, out);
					if(logger.isLoggable(Level.FINER)) {
						logger.finer("Using MediaValidator: " + validator);
					}
					RequestEncodingContext.setCurrentContext(
						request,
						new RequestEncodingContext(newOutputType, validator)
					);
					try {
						doTag(capturedBody, validator);
					} finally {
						RequestEncodingContext.setCurrentContext(request, parentEncodingContext);
					}
				}
			}

			// Write any suffix
			writeSuffix(containerType, containerValidator);
		}
	}

	/**
	 * Invokes the body.  This is only called when a body exists.  Subclasses may override this to perform
	 * actions before and/or after invoking the body.  Any overriding implementation should call
	 * super.invoke(JspFragment,MediaValidator) to invoke the body, unless it wants to suppress the body invocation.
	 * <p>
	 * This implementation invokes {@link JspFragment#invoke(java.io.Writer)}
	 * providing the capture validator.
	 * </p>
	 */
	protected void invoke(JspFragment body, MediaValidator captureValidator) throws JspException, IOException {
		body.invoke(captureValidator);
	}

	/**
	 * <p>
	 * Writes any prefix in the container's media type.
	 * The output must be valid for the provided type.
	 * This will not be called when the output type is {@code null}.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 *
	 * @see  #getOutputType()
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void writePrefix(MediaType containerType, Writer out) throws JspException, IOException {
		// By default, nothing is printed.
	}

	/**
	 * Sets the media encoder options.  This is how subclass tag attributes
	 * can effect the encoding.
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void setMediaEncoderOptions(MediaEncoder mediaEncoder) {
	}

	protected void writeEncoderPrefix(MediaEncoder mediaEncoder, JspWriter out) throws JspException, IOException {
		mediaEncoder.writePrefixTo(out);
	}

	/**
	 * Once the out {@link JspWriter} has been replaced to output the proper content
	 * type, this version of {@link #doTag()} is called.
	 * <p>
	 * The body, if present, has already been invoked and any output captured.
	 * </p>
	 * <p>
	 * This default implementation does nothing.
	 * </p>
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void doTag(BufferResult capturedBody, Writer out) throws JspException, IOException {
		// Do nothing by default
	}

	protected void writeEncoderSuffix(MediaEncoder mediaEncoder, JspWriter out) throws JspException, IOException {
		mediaEncoder.writeSuffixTo(out);
	}

	/**
	 * <p>
	 * Writes any suffix in the container's media type.
	 * The output must be valid for the provided type.
	 * This will not be called when the output type is {@code null}.
	 * </p>
	 * <p>
	 * This default implementation prints nothing.
	 * </p>
	 *
	 * @see  #getOutputType()
	 */
	@SuppressWarnings("NoopMethodInAbstractClass")
	protected void writeSuffix(MediaType containerType, Writer out) throws JspException, IOException {
		// By default, nothing is printed.
	}
}

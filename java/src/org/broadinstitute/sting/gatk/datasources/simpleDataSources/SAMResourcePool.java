/*
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.gatk.datasources.simpleDataSources;

import org.broadinstitute.sting.gatk.iterators.StingSAMIterator;
import org.broadinstitute.sting.gatk.Reads;
import org.apache.log4j.Logger;

import net.sf.picard.sam.SamFileHeaderMerger;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMFileReader;

import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * Maintain a pool of resources of accessors to SAM read data.  SAMFileReaders and
 * headers are actually quite expensive to open, so this class manages the mechanics
 * of keeping them open and reusing them.
 * @author hanna
 * @version 0.1
 */
@Deprecated
class SAMResourcePool extends ResourcePool<ReadStreamResource, StingSAMIterator> {
    /** our log, which we want to capture anything from this class */
    protected static Logger logger = Logger.getLogger(SAMResourcePool.class);

    /** Source information about the reads. */
    protected Reads reads;
    protected SamFileHeaderMerger headerMerger;
    protected Map<File, SAMFileReader> fileToReaderMap;

    /**
     * Do all the constituent BAM files have indices?  We support some very limited
     * cases where not all BAM files have indices available.
     */
    protected final boolean hasIndex;

    /** Is this a by-reads traversal or a by-locus? */
    protected boolean queryOverlapping;

    /** File header for the combined file. */
    protected final SAMFileHeader header;

    public SAMResourcePool( Reads reads ) {
        this.reads = reads;
        this.queryOverlapping = true;

        ReadStreamResource streamResource = createNewResource();
        this.header = streamResource.getHeader();
        this.headerMerger = streamResource.getHeaderMerger();
        this.hasIndex = streamResource.hasIndex();
        this.fileToReaderMap = streamResource.getFileToReaderMapping();

        // Add this resource to the pool.
        this.addNewResource(streamResource);
    }

    /** Get the combined header for all files in the iterator pool. */
    public SAMFileHeader getHeader() {
        return header;
    }

    /**
     * Returns Reads data structure containing information about the reads data sources placed in this pool as well as
     * information about how they are downsampled, sorted, and filtered
     * @return
     */
    public Reads getReadsInfo() { return reads; }
    
    /**
     * Returns a mapping from original input files to the SAMFileReaders
     *
     * @return the mapping
     */
    public Map<File, SAMFileReader> getFileToReaderMapping() {
        return fileToReaderMap;
    }

    /**
     * Returns header merger: a class that keeps the mapping between original read groups and read groups
     * of the merged stream; merger also provides access to the individual file readers (and hence headers
     * too) maintained by the system. 
     * @return
     */
    public SamFileHeaderMerger getHeaderMerger() { return headerMerger; }

    protected ReadStreamResource selectBestExistingResource( DataStreamSegment segment, List<ReadStreamResource> resources ) {
        for (ReadStreamResource resource : resources) {
            if (resource.canAccessSegmentEfficiently(segment)) {
                return resource;
            }
        }
        return null;
    }

    protected ReadStreamResource createNewResource() {
        return new ReadStreamResource(reads);
    }

    protected StingSAMIterator createIteratorFromResource( DataStreamSegment segment, ReadStreamResource streamResource ) {
        StingSAMIterator iterator = null;

        if (!queryOverlapping)
            iterator = streamResource.getReadsContainedBy(segment);
        else
            iterator = streamResource.getReadsOverlapping(segment);

        return new ReleasingIterator( streamResource, iterator );
    }

    protected void closeResource( ReadStreamResource resource ) {
        resource.close();
    }

    private class ReleasingIterator implements StingSAMIterator {
        /**
         * The resource acting as the source of the data.
         */
        private final ReadStreamResource resource;

        /**
         * The iterator to wrap.
         */
        private final StingSAMIterator wrappedIterator;

        public Reads getSourceInfo() {
            return wrappedIterator.getSourceInfo();
        }

        public ReleasingIterator( ReadStreamResource resource, StingSAMIterator wrapped ) {
            this.resource = resource;
            this.wrappedIterator = wrapped;
        }

        public ReleasingIterator iterator() {
            return this;
        }

        public void remove() {
            throw new UnsupportedOperationException("Can't remove from a StingSAMIterator");
        }

        public void close() {
            resource.destroy(wrappedIterator);
            release(this);
        }

        public boolean hasNext() {
            return wrappedIterator.hasNext();
        }

        public SAMRecord next() {
            return wrappedIterator.next();
        }
    }

    public boolean isQueryOverlapping() {
        return queryOverlapping;
    }

    public void setQueryOverlapping( boolean queryOverlapping ) {
        this.queryOverlapping = queryOverlapping;
    }

}

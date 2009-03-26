package org.broadinstitute.sting.playground.gatk.walkers;

import net.sf.samtools.SAMRecord;
import org.broadinstitute.sting.gatk.LocusContext;
import org.broadinstitute.sting.gatk.walkers.ReadWalker;
import org.broadinstitute.sting.utils.Utils;
import edu.mit.broad.picard.reference.ReferenceSequence;

import java.util.Iterator;
import java.util.List;
import static java.lang.reflect.Array.*;

public class BaseQualityDumpWalker extends ReadWalker<Integer, Integer> {

    protected final int MIN_TARGET_EDIT_DISTANCE = 0; //5;
    protected final int MAX_TARGET_EDIT_DISTANCE = 4; //10;

    public String getName() {
        return "Base_Quality_Dump";
    }

    // Do we actually want to operate on the context?
    public boolean filter(LocusContext context, SAMRecord read) {
	    // we only want aligned reads
	    return !read.getReadUnmappedFlag();
    }

    public Integer map(LocusContext context, SAMRecord read) {

        int editDist = Integer.parseInt(read.getAttribute("NM").toString());

        // ignore alignments with indels for now
        if ( read.getAlignmentBlocks().size() == 1 &&
             editDist >= MIN_TARGET_EDIT_DISTANCE &&
             editDist <= MAX_TARGET_EDIT_DISTANCE ) {

            String qualStr = read.getBaseQualityString();
            int[] scores = new int[qualStr.length()];
            boolean reverseFlag = read.getReadNegativeStrandFlag();
            for ( int i = 0; i < qualStr.length(); i++)
                scores[(reverseFlag ? (qualStr.length()-1-i) : i)] += (int)qualStr.charAt(i) - 33;
            for ( int i = 0; i < scores.length; i++ )
                System.out.print(scores[i] + " ");
            System.out.println("");
        }

        return 1;
    }

    public Integer reduceInit() { return 0; }

    public Integer reduce(Integer value, Integer sum) {
        return value + sum;
    }
}

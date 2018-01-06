package org.deeplearning4j.datasets.iterator;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.exception.ND4JIllegalStateException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This iterator virtually splits given MultiDataSetIterator into Train and Test parts.
 *
 * PLEASE NOTE: You can't use Test iterator twice in a row.
 * PLEASE NOTE: You can't use this iterator, if underlying iterator uses randomization/shuffle between epochs.
 *
 * @author raver119@gmail.com
 */
@Slf4j
public class MultiDataSetIteratorSplitter {
    protected MultiDataSetIterator backedIterator;
    protected final long totalExamples;
    protected final double ratio;
    protected final long numTrain;
    protected final long numTest;

    protected AtomicLong counter = new AtomicLong(0);

    protected AtomicBoolean resetPending = new AtomicBoolean(false);
    protected org.nd4j.linalg.dataset.MultiDataSet firstTrain;

    /**
     *
     * @param baseIterator
     * @param totalExamples - total number of examples in underlying iterator. this value will be used to determine number of test/train examples
     * @param ratio - this value will be used as splitter. should be between in range of 0.0 > X < 1.0. I.e. if value 0.7 is provided, then 70% of total examples will be used for training, and 30% of total examples will be used for testing
     */
    public MultiDataSetIteratorSplitter(@NonNull MultiDataSetIterator baseIterator, long totalExamples, double ratio) {
        if (!(ratio > 0.0 && ratio < 1.0))
            throw new ND4JIllegalStateException("Ratio value should be in range of 0.0 > X < 1.0");

        if (totalExamples < 0)
            throw new ND4JIllegalStateException("totalExamples number should be positive value");

        if (!baseIterator.resetSupported())
            throw new ND4JIllegalStateException("Underlying iterator doesn't support reset, so it can't be used for runtime-split");


        this.backedIterator = baseIterator;
        this.totalExamples = totalExamples;
        this.ratio = ratio;
        this.numTrain = (long) (totalExamples * ratio);
        this.numTest = totalExamples - numTrain;

        log.warn("IteratorSplitter is used: please ensure you don't use randomization/shuffle in underlying iterator!");
    }

    public MultiDataSetIterator getTrainIterator() {
        return new MultiDataSetIterator() {
            @Override
            public MultiDataSet next(int num) {
                throw new UnsupportedOperationException("To be implemented yet");
            }

            @Override
            public void setPreProcessor(MultiDataSetPreProcessor preProcessor) {
                backedIterator.setPreProcessor(preProcessor);
            }

            @Override
            public MultiDataSetPreProcessor getPreProcessor() {
                return backedIterator.getPreProcessor();
            }

            @Override
            public boolean resetSupported() {
                return backedIterator.resetSupported();
            }

            @Override
            public boolean asyncSupported() {
                return backedIterator.asyncSupported();
            }

            @Override
            public void reset() {
                resetPending.set(true);
            }

            @Override
            public boolean hasNext() {
                if (resetPending.get()) {
                    if (resetSupported()) {
                        backedIterator.reset();
                        counter.set(0);
                        resetPending.set(false);
                    } else
                        throw new UnsupportedOperationException("Reset isn't supported by underlying iterator");
                }

                val state = backedIterator.hasNext();
                if (state && counter.get() < numTrain)
                    return true;
                else
                    return false;
            }

            @Override
            public MultiDataSet next() {
                counter.incrementAndGet();
                val p = backedIterator.next();

                if (counter.get() == 1 && firstTrain == null) {
                    // first epoch ever, we'll save first dataset and will use it to check for equality later
                    firstTrain = (org.nd4j.linalg.dataset.MultiDataSet) p.copy();
                    firstTrain.detach();
                } else if (counter.get() == 1) {
                    // epoch > 1, comparing first dataset to previously stored dataset. they should be equal
                    int cnt = 0;
                    for (val c: p.getFeatures())
                        if (!c.equalsWithEps(firstTrain.getFeatures()[cnt++], 1e-5))
                            throw new ND4JIllegalStateException("First examples do not match. Randomization was used?");
                }

                return p;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public MultiDataSetIterator getTestIterator() {
        return new MultiDataSetIterator() {
            @Override
            public MultiDataSet next(int num) {
                throw new UnsupportedOperationException("To be implemented yet");
            }

            @Override
            public void setPreProcessor(MultiDataSetPreProcessor preProcessor) {
                backedIterator.setPreProcessor(preProcessor);
            }

            @Override
            public MultiDataSetPreProcessor getPreProcessor() {
                return backedIterator.getPreProcessor();
            }

            @Override
            public boolean resetSupported() {
                return backedIterator.resetSupported();
            }

            @Override
            public boolean asyncSupported() {
                return backedIterator.asyncSupported();
            }


            @Override
            public void reset() {
                resetPending.set(true);
            }

            @Override
            public boolean hasNext() {
                val state = backedIterator.hasNext();
                if (state && counter.get() < numTrain + numTest)
                    return true;
                else
                    return false;
            }

            @Override
            public MultiDataSet next() {
                return backedIterator.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}

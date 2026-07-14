package iq.base;

import org.rumbledb.api.Item;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.exceptions.RumbleException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Class used to save the result of a query evaluation
 * Because we also test with errors, it can either contain a normal result or error
 */
class QueryEvaluation {
    private final Supplier<SequenceOfItems> sequenceSupplier;
    private final RumbleException error;
    private List<Item> result;
    private String serializedResult;

    private QueryEvaluation(Supplier<SequenceOfItems> sequenceSupplier, RumbleException error) {
        this.sequenceSupplier = sequenceSupplier;
        this.error = error;
    }

    static QueryEvaluation withResult(Supplier<SequenceOfItems> sequenceSupplier) {
        return new QueryEvaluation(sequenceSupplier, null);
    }

    static QueryEvaluation withError(RumbleException error) {
        return new QueryEvaluation(null, error);
    }

    List<Item> getResult() {
        if (this.error != null) {
            throw this.error;
        }
        if (this.result == null) {
            SequenceOfItems sequence = this.sequenceSupplier.get();
            List<Item> materializedResult = new ArrayList<>();
            sequence.populateList(materializedResult, 0);
            this.result = materializedResult;
        }
        return this.result;
    }

    String getSerializedResult() {
        if (this.error != null) {
            throw this.error;
        }
        if (this.serializedResult == null) {
            this.serializedResult = this.sequenceSupplier.get().serialize();
        }
        return this.serializedResult;
    }

    RumbleException getError() {
        return this.error;
    }
}

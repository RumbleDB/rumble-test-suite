package iq.base;

import org.rumbledb.api.Item;
import org.rumbledb.api.SequenceOfItems;
import org.rumbledb.exceptions.RumbleException;

import java.util.List;

/**
 * Class used to save the result of a query evaluation
 * Because we also test with errors, it can either contain a normal result or error
 */
class QueryEvaluation {
    private final SequenceOfItems result;
    private final RumbleException error;

    private QueryEvaluation(SequenceOfItems result, RumbleException error) {
        this.result = result;
        this.error = error;
    }

    static QueryEvaluation withResult(SequenceOfItems result) {
        return new QueryEvaluation(result, null);
    }

    static QueryEvaluation withError(RumbleException error) {
        return new QueryEvaluation(null, error);
    }

    List<Item> getResult() {
        if (this.error != null) {
            throw this.error;
        }
        return this.result.getAsList();
    }

    String getSerializedResult() {
        if (this.error != null) {
            throw this.error;
        }
        return this.result.serialize();
    }

    RumbleException getError() {
        return this.error;
    }
}

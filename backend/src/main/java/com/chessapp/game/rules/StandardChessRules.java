package com.chessapp.game.rules;

import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.ChessPosition;

import java.util.Objects;

/**
 * @author pari on 10/24/26
 */
public class StandardChessRules implements ChessRules {

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private final FenParser fenParser;

    public StandardChessRules(FenParser fenParser) {
        this.fenParser = Objects.requireNonNull(
                fenParser,
                "FEN parser is required"
        );
    }

    // Using our parser avoids maintaining separate FEN and programmatic board definitions.
    // The tradeoff is parsing a small string for each creation.
    // That is insignificant for this use case; immutable-position caching can be considered if a concrete need appears.
    @Override
    public ChessPosition initialPosition() {
        return fenParser.parse(INITIAL_FEN);
    }

}

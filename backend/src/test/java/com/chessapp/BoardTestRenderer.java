package com.chessapp;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Piece;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.Square;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author pari on 10/24/26
 * The renderer scans all 64 squares from White’s perspective.
 * For each square, it retrieves the piece symbol and adds a marker if the square is the source or a candidate destination.
 * It only displays the supplied moves; it does not calculate them.
 */
public final class BoardTestRenderer {

    private BoardTestRenderer() {
    }

    public static String render(
            ChessPosition position,
            Square source,
            Set<Move> moves
    ) {
        var destinations = moves.stream()
                .map(Move::to)
                .collect(Collectors.toSet());

        var output = new StringBuilder();

        // Display rank 8 first, matching the usual view from White's side.
        for (int rank = 7; rank >= 0; rank--) {
            output.append(rank + 1).append(" ");

            for (int file = 0; file < 8; file++) {
                var square = new Square(file, rank);
                String symbol = position.board().pieceAt(square)
                        .map(BoardTestRenderer::symbol)
                        .orElse(".");

                if (square.equals(source)) {
                    output.append("<").append(symbol).append(">");
                } else if (destinations.contains(square)) {
                    output.append("[")
                            .append(symbol.equals(".") ? "*" : symbol)
                            .append("]");
                } else {
                    output.append(" ").append(symbol).append(" ");
                }
            }

            output.append("\n");
        }

        output.append("   a  b  c  d  e  f  g  h\n");

        // Sets have no guaranteed iteration order; sort for stable output.
        String moveList = moves.stream()
                .map(move -> move.from().toAlgebraic()
                        + "->"
                        + move.to().toAlgebraic())
                .sorted()
                .collect(Collectors.joining(", "));

        output.append("Candidates: ")
                .append(moveList.isEmpty() ? "(none)" : moveList)
                .append("\n");

        return output.toString();
    }

    private static String symbol(Piece piece) {
        char value = switch (piece.type()) {
            case KING -> 'k';
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            case PAWN -> 'p';
        };

        return String.valueOf(
                piece.color() == PieceColor.WHITE
                        ? Character.toUpperCase(value)
                        : value
        );
    }
}

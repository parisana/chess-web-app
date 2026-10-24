package com.chessapp.game.config;

import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.StandardChessRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * @author pari on 10/24/26
 */
@Configuration(proxyBeanMethods = false)
public class GameRulesConfig {

    @Bean
    FenParser fenParser() {
        return new FenParser();
    }

    @Bean
    FenSerializer fenSerializer() {
        return new FenSerializer();
    }

    @Bean
    ChessRules chessRules(FenParser fenParser) {
        return new StandardChessRules(fenParser);
    }
}

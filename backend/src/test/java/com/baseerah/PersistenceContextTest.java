package com.baseerah;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.baseerah.account.AccountRepository;
import com.baseerah.client.ClientRepository;
import com.baseerah.transaction.TransactionRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full application context against the local Liquibase-managed PostgreSQL (started via
 * {@code docker-compose up}, per the project run model) with {@code spring.jpa.hibernate.ddl-auto=validate}.
 * Because Hibernate runs in {@code validate} mode, a successful context start proves the
 * {@code Client}/{@code Account}/{@code Transaction} entities match every column and type in the V1 schema
 * (DESIGN.md §4.2) with no drift. The test then asserts the three repositories are wired and queryable.
 *
 * <p>The class is skipped (not failed) when Postgres is not reachable on {@code localhost:5432}, so a fresh
 * checkout without the database running does not produce a spurious failure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("com.baseerah.PersistenceContextTest#postgresReachable")
class PersistenceContextTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void repositoriesAreWiredAndQueryableAgainstValidatedSchema() {
        assertThat(clientRepository).isNotNull();
        assertThat(accountRepository).isNotNull();
        assertThat(transactionRepository).isNotNull();

        // A trivial round-trip confirms each mapping is valid against the live, validated schema.
        assertThatCode(() -> {
            clientRepository.count();
            accountRepository.count();
            transactionRepository.count();
        }).doesNotThrowAnyException();
    }

    /** Fast TCP probe so the suite skips cleanly when the local Postgres is not up. */
    @SuppressWarnings("unused")
    static boolean postgresReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

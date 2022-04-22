package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.mirror.importer.MirrorProperties;

import com.sun.jdi.Mirror;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.inject.Named;
import java.util.concurrent.atomic.AtomicReference;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class BlockNumberMigration extends MirrorBaseJavaMigration {

    private final JdbcTemplate jdbcTemplate;

    private final BlockNumberMigrationProperties migrationProperties;

    private final MirrorProperties mirrorProperties;

    @Override
    protected void doMigrate() {
        if (shouldNotMigrateOnCurrentNetwork()) {
            return ;
        }

        if (!migrationProperties.isEnabled()) {
            return ;
        }
        Long recordFileBlockNumber = getRecordFileByConsensusEnd(migrationProperties.getCorrectConsensusEnd());
        if (recordFileBlockNumber == null) {
            return ;
        }

        if (migrationProperties.getCorrectBlockNumber() == recordFileBlockNumber) {
            return ;
        }

        updateRecordFilesBlockNumber(migrationProperties.getCorrectBlockNumber(), recordFileBlockNumber);
    }

    private boolean shouldNotMigrateOnCurrentNetwork() {
        MirrorProperties.HederaNetwork currentNetwork = mirrorProperties.getNetwork();
        return !currentNetwork.equals(MirrorProperties.HederaNetwork.TESTNET) &&
                !currentNetwork.equals(MirrorProperties.HederaNetwork.MAINNET);
    }

    private void updateRecordFilesBlockNumber(long correctBlockNumber, long incorrectBlockNumber) {
        jdbcTemplate.execute("drop index record_file__index");
        long offset = correctBlockNumber - incorrectBlockNumber;
        jdbcTemplate.update("update record_file set index = index + ?", offset);
        jdbcTemplate.execute("create unique index record_file__index on record_file(index)");
    }

    private Long getRecordFileByConsensusEnd(long consensusEnd) {
        AtomicReference<Long> atomicReference = new AtomicReference<>();
        jdbcTemplate.query("select index from record_file where consensus_end = ?",rse -> {
            atomicReference.set(rse.getLong(1));
        },consensusEnd);
        return atomicReference.get();
    }

    @Override
    public MigrationVersion getVersion() {
        return null;
    }

    @Override
    public Integer getChecksum() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Updates the incorrect index from the record file table when necessary.";
    }
}

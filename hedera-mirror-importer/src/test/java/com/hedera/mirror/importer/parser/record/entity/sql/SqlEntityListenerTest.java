package com.hedera.mirror.importer.parser.record.entity.sql;

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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.SCHEDULE;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NonFeeTransfer;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.mirror.importer.repository.CryptoAllowanceRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EthereumTransactionRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NftAllowanceRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.repository.TransactionSignatureRepository;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class SqlEntityListenerTest extends IntegrationTest {
    private static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    private static final String KEY2 = "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    private static final EntityId TRANSACTION_PAYER = EntityId.of("0.0.1000", ACCOUNT);

    private final ContractLogRepository contractLogRepository;
    private final ContractRepository contractRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final CryptoTransferRepository cryptoTransferRepository;
    private final DomainBuilder domainBuilder;
    private final EntityRepository entityRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;
    private final FileDataRepository fileDataRepository;
    private final LiveHashRepository liveHashRepository;
    private final NftRepository nftRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftTransferRepository nftTransferRepository;
    private final RecordFileRepository recordFileRepository;
    private final ScheduleRepository scheduleRepository;
    private final SqlEntityListener sqlEntityListener;
    private final SqlProperties sqlProperties;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final TokenRepository tokenRepository;
    private final TokenTransferRepository tokenTransferRepository;
    private final TopicMessageRepository topicMessageRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionSignatureRepository transactionSignatureRepository;
    private final TransactionTemplate transactionTemplate;

    private static Key keyFromString(String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build();
    }

    private static Stream<Arguments> provideParamsContractHistory() {
        Consumer<Contract.ContractBuilder> emptyCustomizer = c -> {
        };
        Consumer<Contract.ContractBuilder> initcodeCustomizer = c -> c.fileId(null).initcode(new byte[] {1, 2, 3, 4});
        return Stream.of(
                Arguments.of("fileId", emptyCustomizer, 1),
                Arguments.of("fileId", emptyCustomizer, 2),
                Arguments.of("fileId", emptyCustomizer, 3),
                Arguments.of("initcode", initcodeCustomizer, 1),
                Arguments.of("initcode", initcodeCustomizer, 2),
                Arguments.of("initcode", initcodeCustomizer, 3)
        );
    }

    @BeforeEach
    final void beforeEach() {
        sqlProperties.setBatchSize(20_000);
        sqlEntityListener.onStart();
    }

    @Test
    void executeBatch() {
        // given
        sqlProperties.setBatchSize(1);
        Entity entity1 = domainBuilder.entity().get();
        Entity entity2 = domainBuilder.entity().get();

        // when
        sqlEntityListener.onEntity(entity1);
        sqlEntityListener.onEntity(entity2);
        completeFileAndCommit();

        // then
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @Test
    void isEnabled() {
        sqlProperties.setEnabled(false);
        assertThat(sqlEntityListener.isEnabled()).isFalse();

        sqlProperties.setEnabled(true);
        assertThat(sqlEntityListener.isEnabled()).isTrue();
    }

    @Test
    void onContract() {
        // given
        Contract contract1 = domainBuilder.contract().get();
        Contract contract2 = domainBuilder.contract()
                .customize(c -> c.evmAddress(null).fileId(null).initcode(domainBuilder.bytes(1024)))
                .get();

        // when
        sqlEntityListener.onContract(contract1);
        sqlEntityListener.onContract(contract2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(contractRepository.findAll()).containsExactlyInAnyOrder(contract1, contract2);
        assertThat(findHistory(Contract.class)).isEmpty();
    }

    @ParameterizedTest(name = "{2} record files with {0}")
    @MethodSource("provideParamsContractHistory")
    void onContractHistory(String bytecodeSource, Consumer<Contract.ContractBuilder> customizer, int commitIndex) {
        // given
        Contract contractCreate = domainBuilder.contract()
                .customize(c -> c.obtainerId(null))
                .customize(customizer)
                .get();

        Contract contractUpdate = contractCreate.toEntityId().toEntity();
        contractUpdate.setAutoRenewPeriod(30L);
        contractUpdate.setExpirationTimestamp(500L);
        contractUpdate.setKey(domainBuilder.key());
        contractUpdate.setMaxAutomaticTokenAssociations(100);
        contractUpdate.setMemo("updated");
        contractUpdate.setTimestampLower(contractCreate.getTimestampLower() + 1);
        contractUpdate.setProxyAccountId(EntityId.of(100L, ACCOUNT));

        Contract contractDelete = contractCreate.toEntityId().toEntity();
        contractDelete.setDeleted(true);
        contractDelete.setTimestampLower(contractCreate.getTimestampLower() + 2);
        contractDelete.setObtainerId(EntityId.of(999L, EntityType.CONTRACT));

        // Expected merged objects
        Contract mergedCreate = TestUtils.clone(contractCreate);
        Contract mergedUpdate = TestUtils.merge(contractCreate, contractUpdate);
        Contract mergedDelete = TestUtils.merge(mergedUpdate, contractDelete);
        mergedCreate.setTimestampUpper(contractUpdate.getTimestampLower());

        // when
        sqlEntityListener.onContract(contractCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(contractRepository.findAll()).containsExactly(contractCreate);
            assertThat(findHistory(Contract.class)).isEmpty();
        }

        sqlEntityListener.onContract(contractUpdate);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(contractRepository.findAll()).containsExactly(mergedUpdate);
            assertThat(findHistory(Contract.class)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onContract(contractDelete);
        completeFileAndCommit();

        // then
        mergedUpdate.setTimestampUpper(contractDelete.getTimestampLower());
        assertThat(contractRepository.findAll()).containsExactly(mergedDelete);
        assertThat(findHistory(Contract.class)).containsExactly(mergedCreate, mergedUpdate);
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void onContractLog() {
        // given
        ContractLog contractLog = domainBuilder.contractLog().get();

        // when
        sqlEntityListener.onContractLog(contractLog);
        completeFileAndCommit();

        // then
        assertThat(contractLogRepository.findAll()).containsExactlyInAnyOrder(contractLog);
    }

    @Test
    void onContractResult() {
        // given
        ContractResult contractResult = domainBuilder.contractResult().get();

        // when
        sqlEntityListener.onContractResult(contractResult);
        completeFileAndCommit();

        // then
        assertThat(contractResultRepository.findAll()).containsExactlyInAnyOrder(contractResult);
    }

    @Test
    void onContractStateChange() {
        // given
        ContractStateChange contractStateChange = domainBuilder.contractStateChange().get();

        // when
        sqlEntityListener.onContractStateChange(contractStateChange);
        completeFileAndCommit();

        // then
        assertThat(contractStateChangeRepository.findAll()).containsExactlyInAnyOrder(contractStateChange);
    }

    @Test
    void onCryptoAllowance() {
        // given
        CryptoAllowance cryptoAllowance1 = domainBuilder.cryptoAllowance().get();
        CryptoAllowance cryptoAllowance2 = domainBuilder.cryptoAllowance().get();

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowance1);
        sqlEntityListener.onCryptoAllowance(cryptoAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(cryptoAllowanceRepository.findAll()).containsExactlyInAnyOrder(cryptoAllowance1, cryptoAllowance2);
        assertThat(findHistory(CryptoAllowance.class, "payer_account_id, spender")).isEmpty();
    }

    @ValueSource(ints = {1, 2, 3})
    @ParameterizedTest
    void onCryptoAllowanceHistory(int commitIndex) {
        // given
        final String idColumns = "payer_account_id, spender";
        var builder = domainBuilder.cryptoAllowance();
        CryptoAllowance cryptoAllowanceCreate = builder.get();

        CryptoAllowance cryptoAllowanceUpdate1 = builder.customize(c -> c.amount(999L)).get();
        cryptoAllowanceUpdate1.setTimestampLower(cryptoAllowanceCreate.getTimestampLower() + 1);

        CryptoAllowance cryptoAllowanceUpdate2 = builder.customize(c -> c.amount(0L)).get();
        cryptoAllowanceUpdate2.setTimestampLower(cryptoAllowanceCreate.getTimestampLower() + 2);

        // Expected merged objects
        CryptoAllowance mergedCreate = TestUtils.clone(cryptoAllowanceCreate);
        CryptoAllowance mergedUpdate1 = TestUtils.merge(cryptoAllowanceCreate, cryptoAllowanceUpdate1);
        CryptoAllowance mergedUpdate2 = TestUtils.merge(mergedUpdate1, cryptoAllowanceUpdate2);
        mergedCreate.setTimestampUpper(cryptoAllowanceUpdate1.getTimestampLower());

        // when
        sqlEntityListener.onCryptoAllowance(cryptoAllowanceCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowanceCreate);
            assertThat(findHistory(CryptoAllowance.class, idColumns)).isEmpty();
        }

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceUpdate1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(cryptoAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
            assertThat(findHistory(CryptoAllowance.class, idColumns)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onCryptoAllowance(cryptoAllowanceUpdate2);
        completeFileAndCommit();

        // then
        mergedUpdate1.setTimestampUpper(cryptoAllowanceUpdate2.getTimestampLower());
        assertThat(cryptoAllowanceRepository.findAll()).containsExactly(mergedUpdate2);
        assertThat(findHistory(CryptoAllowance.class, idColumns)).containsExactly(mergedCreate, mergedUpdate1);
    }

    @Test
    void onCryptoTransfer() {
        // given
        var cryptoTransfer1 = domainBuilder.cryptoTransfer().get();
        var cryptoTransfer2 = domainBuilder.cryptoTransfer().get();

        // when
        sqlEntityListener.onCryptoTransfer(cryptoTransfer1);
        sqlEntityListener.onCryptoTransfer(cryptoTransfer2);
        completeFileAndCommit();

        // then
        assertThat(cryptoTransferRepository.findAll()).containsExactlyInAnyOrder(cryptoTransfer1, cryptoTransfer2);
    }

    @Test
    void onEndNull() {
        sqlEntityListener.onEnd(null);
        assertThat(recordFileRepository.count()).isZero();
    }

    @Test
    void onEntity() {
        // given
        Entity entity1 = domainBuilder.entity().get();
        Entity entity2 = domainBuilder.entity().get();

        // when
        sqlEntityListener.onEntity(entity1);
        sqlEntityListener.onEntity(entity2);
        completeFileAndCommit();

        // then
        assertThat(contractRepository.count()).isZero();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(Entity.class)).isEmpty();
    }

    @ValueSource(ints = {1, 2, 3})
    @ParameterizedTest
    void onEntityHistory(int commitIndex) {
        // given
        Entity entityCreate = domainBuilder.entity().get();

        Entity entityUpdate = entityCreate.toEntityId().toEntity();
        entityUpdate.setAlias(entityCreate.getAlias());
        entityUpdate.setAutoRenewAccountId(EntityId.of(101L, ACCOUNT));
        entityUpdate.setAutoRenewPeriod(30L);
        entityUpdate.setExpirationTimestamp(500L);
        entityUpdate.setKey(domainBuilder.key());
        entityUpdate.setMaxAutomaticTokenAssociations(40);
        entityUpdate.setMemo("updated");
        entityUpdate.setTimestampLower(entityCreate.getTimestampLower() + 1);
        entityUpdate.setProxyAccountId(EntityId.of(100L, ACCOUNT));
        entityUpdate.setReceiverSigRequired(true);
        entityUpdate.setSubmitKey(domainBuilder.key());

        Entity entityDelete = entityCreate.toEntityId().toEntity();
        entityDelete.setAlias(entityCreate.getAlias());
        entityDelete.setDeleted(true);
        entityDelete.setTimestampLower(entityCreate.getTimestampLower() + 2);

        // Expected merged objects
        Entity mergedCreate = TestUtils.clone(entityCreate);
        Entity mergedUpdate = TestUtils.merge(entityCreate, entityUpdate);
        Entity mergedDelete = TestUtils.merge(mergedUpdate, entityDelete);
        mergedCreate.setTimestampUpper(entityUpdate.getTimestampLower());

        // when
        sqlEntityListener.onEntity(entityCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(entityCreate);
            assertThat(findHistory(Entity.class)).isEmpty();
        }

        sqlEntityListener.onEntity(entityUpdate);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(entityRepository.findAll()).containsExactly(mergedUpdate);
            assertThat(findHistory(Entity.class)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onEntity(entityDelete);
        completeFileAndCommit();

        // then
        mergedUpdate.setTimestampUpper(entityDelete.getTimestampLower());
        assertThat(entityRepository.findAll()).containsExactly(mergedDelete);
        assertThat(findHistory(Entity.class)).containsExactly(mergedCreate, mergedUpdate);
    }

    @Test
    void onNonFeeTransfer() {
        // given
        NonFeeTransfer nonFeeTransfer1 = domainBuilder.nonFeeTransfer().customize(n -> n
                .amount(1L)
                .consensusTimestamp(1L)
                .entityId(EntityId.of(1L, ACCOUNT))
                .payerAccountId(TRANSACTION_PAYER)).get();
        NonFeeTransfer nonFeeTransfer2 = domainBuilder.nonFeeTransfer().customize(n -> n
                .amount(2L)
                .consensusTimestamp(2L)
                .entityId(EntityId.of(2L, ACCOUNT))
                .payerAccountId(TRANSACTION_PAYER)).get();

        // when
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer1);
        sqlEntityListener.onNonFeeTransfer(nonFeeTransfer2);
        completeFileAndCommit();

        // then
        assertThat(findNonFeeTransfers()).containsExactlyInAnyOrder(nonFeeTransfer1, nonFeeTransfer2);
    }

    @Test
    void onTopicMessage() {
        // given
        TopicMessage topicMessage = getTopicMessage();

        // when
        sqlEntityListener.onTopicMessage(topicMessage);
        completeFileAndCommit();

        // then
        assertThat(topicMessageRepository.findAll()).containsExactlyInAnyOrder(topicMessage);
    }

    @Test
    void onFileData() {
        // given
        FileData expectedFileData = new FileData(11L, Strings.toByteArray("file data"), EntityId
                .of(0, 0, 111, EntityType.FILE), TransactionType.CONSENSUSSUBMITMESSAGE.getProtoId());

        // when
        sqlEntityListener.onFileData(expectedFileData);
        completeFileAndCommit();

        // then
        assertThat(fileDataRepository.findAll()).containsExactlyInAnyOrder(expectedFileData);
    }

    @Test
    void onLiveHash() {
        // given
        LiveHash expectedLiveHash = new LiveHash(20L, "live hash".getBytes());

        // when
        sqlEntityListener.onLiveHash(expectedLiveHash);
        completeFileAndCommit();

        // then
        assertThat(liveHashRepository.findAll()).containsExactlyInAnyOrder(expectedLiveHash);
    }

    @Test
    void onTransaction() {
        // given
        var firstTransaction = domainBuilder.transaction().get();
        var secondTransaction = domainBuilder.transaction().get();
        var thirdTransaction = domainBuilder.transaction().get();

        // when
        sqlEntityListener.onTransaction(firstTransaction);
        sqlEntityListener.onTransaction(secondTransaction);
        sqlEntityListener.onTransaction(thirdTransaction);
        completeFileAndCommit();

        // then
        assertThat(transactionRepository.findAll())
                .containsExactlyInAnyOrder(firstTransaction, secondTransaction, thirdTransaction);

        assertThat(transactionRepository.findById(firstTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .extracting(Transaction::getIndex)
                .isEqualTo(0);

        assertThat(transactionRepository.findById(secondTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .extracting(Transaction::getIndex)
                .isEqualTo(1);

        assertThat(transactionRepository.findById(thirdTransaction.getConsensusTimestamp()))
                .get()
                .isNotNull()
                .extracting(Transaction::getIndex)
                .isEqualTo(2);
    }

    @Test
    void onEntityMerge() {
        // given
        Entity entity = getEntity(1, 1L, 1L, 0, "memo", keyFromString(KEY));
        sqlEntityListener.onEntity(entity);

        Entity entityAutoUpdated = getEntity(1, 5L);
        EntityId autoRenewAccountId = EntityId.of("0.0.10", ACCOUNT);
        entityAutoUpdated.setAutoRenewAccountId(autoRenewAccountId);
        entityAutoUpdated.setAutoRenewPeriod(360L);
        sqlEntityListener.onEntity(entityAutoUpdated);

        Entity entityExpirationUpdated = getEntity(1, 10L);
        entityExpirationUpdated.setExpirationTimestamp(720L);
        sqlEntityListener.onEntity(entityExpirationUpdated);

        Entity entitySubmitKeyUpdated = getEntity(1, 15L);
        entitySubmitKeyUpdated.setSubmitKey(keyFromString(KEY2).toByteArray());
        sqlEntityListener.onEntity(entitySubmitKeyUpdated);

        Entity entityMemoUpdated = getEntity(1, 20L);
        entityMemoUpdated.setMemo("memo-updated");
        sqlEntityListener.onEntity(entityMemoUpdated);

        Entity entityMaxAutomaticTokenAssociationsUpdated = getEntity(1, 25L);
        entityMaxAutomaticTokenAssociationsUpdated.setMaxAutomaticTokenAssociations(10);
        sqlEntityListener.onEntity(entityMaxAutomaticTokenAssociationsUpdated);

        Entity entityReceiverSigRequired = getEntity(1, 30L);
        entityReceiverSigRequired.setReceiverSigRequired(true);
        sqlEntityListener.onEntity(entityReceiverSigRequired);

        // when
        completeFileAndCommit();

        // then
        Entity expected = getEntity(1, 1L, 30L, "memo-updated", keyFromString(KEY), autoRenewAccountId, 360L, null,
                720L, 10, true, keyFromString(KEY2));
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(expected);
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void onNft() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.4", ACCOUNT);
        EntityId treasuryId = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        // create nft 1
        sqlEntityListener.onNft(getNft(tokenId1, 1L, null, 3L, false, metadata1, 3L)); // mint
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId1, null, null, null, 3L)); // transfer

        // create nft 2
        sqlEntityListener.onNft(getNft(tokenId2, 1L, null, 4L, false, metadata2, 4L)); // mint
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId2, null, null, null, 4L)); // transfer

        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftAllowance() {
        // given
        NftAllowance nftAllowance1 = domainBuilder.nftAllowance().get();
        NftAllowance nftAllowance2 = domainBuilder.nftAllowance().get();

        // when
        sqlEntityListener.onNftAllowance(nftAllowance1);
        sqlEntityListener.onNftAllowance(nftAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(nftAllowanceRepository.findAll()).containsExactlyInAnyOrder(nftAllowance1, nftAllowance2);
        assertThat(findHistory(NftAllowance.class, "payer_account_id, spender, token_id")).isEmpty();
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onNftAllowanceHistory(int commitIndex) {
        // given
        final String idColumns = "payer_account_id, spender, token_id";
        var builder = domainBuilder.nftAllowance();
        NftAllowance nftAllowanceCreate = builder.customize(c -> c.approvedForAll(true)).get();

        NftAllowance nftAllowanceUpdate1 = builder.get();
        nftAllowanceUpdate1.setTimestampLower(nftAllowanceCreate.getTimestampLower() + 1);

        // Expected merged objects
        NftAllowance mergedCreate = TestUtils.clone(nftAllowanceCreate);
        NftAllowance mergedUpdate1 = TestUtils.merge(nftAllowanceCreate, nftAllowanceUpdate1);
        mergedCreate.setTimestampUpper(nftAllowanceUpdate1.getTimestampLower());

        // when
        sqlEntityListener.onNftAllowance(nftAllowanceCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(nftAllowanceRepository.findAll()).containsExactly(nftAllowanceCreate);
            assertThat(findHistory(NftAllowance.class, idColumns)).isEmpty();
        }

        sqlEntityListener.onNftAllowance(nftAllowanceUpdate1);
        completeFileAndCommit();

        // then
        assertThat(nftAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
        assertThat(findHistory(NftAllowance.class, idColumns)).containsExactly(mergedCreate);
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onNftWithInstanceAllowance(int commitIndex) {
        // given
        var nft = domainBuilder.nft().persist();

        // grant allowance
        var expectedNft = TestUtils.clone(nft);
        expectedNft.setDelegatingSpender(domainBuilder.entityId(ACCOUNT));
        expectedNft.setModifiedTimestamp(domainBuilder.timestamp());
        expectedNft.setSpender(domainBuilder.entityId(ACCOUNT));

        var nftUpdate = TestUtils.clone(expectedNft);
        nftUpdate.setCreatedTimestamp(null);

        sqlEntityListener.onNft(nftUpdate);
        if (commitIndex > 1) {
            // when
            completeFileAndCommit();
            // then
            assertThat(nftRepository.findAll()).containsOnly(expectedNft);
        }

        // revoke allowance
        expectedNft = TestUtils.clone(nft);
        expectedNft.setModifiedTimestamp(domainBuilder.timestamp());

        nftUpdate = TestUtils.clone(expectedNft);
        nftUpdate.setCreatedTimestamp(null);
        sqlEntityListener.onNft(nftUpdate);

        // when
        completeFileAndCommit();

        // then
        assertThat(nftRepository.findAll()).containsOnly(expectedNft);
    }

    @Test
    void onNftMintOutOfOrder() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.4", ACCOUNT);
        EntityId treasuryId = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);

        // create nft 1 w transfer coming first
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId1, null, null, null, 3L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId1, 1L, null, 3L, false, metadata1, 3L)); // mint

        // create nft 2 w transfer coming first
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId2, null, null, null, 4L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId2, 1L, null, 4L, false, metadata2, 4L)); // mint

        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftDomainTransfer() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.4", ACCOUNT);
        EntityId treasuryId = EntityId.of("0.0.98", ACCOUNT);
        EntityId accountId3 = EntityId.of("0.0.5", ACCOUNT);
        EntityId accountId4 = EntityId.of("0.0.6", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasuryId, 1L, 1L);
        Token token2 = getToken(tokenId2, treasuryId, 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId2, 1L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined
        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);
        completeFileAndCommit();
        assertEquals(2, nftRepository.count());

        // nft w transfers
        sqlEntityListener.onNft(getNft(tokenId1, 1L, accountId3, null, null, null, 5L)); // transfer
        sqlEntityListener.onNft(getNft(tokenId2, 1L, accountId4, null, null, null, 6L)); // transfer
        completeFileAndCommit();

        Nft nft1 = getNft(tokenId1, 1L, accountId3, 3L, false, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId2, 1L, accountId4, 4L, false, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftTransferOwnershipAndDelete() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.3", ACCOUNT);
        EntityId treasury = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasury, 1L, 1L);
        sqlEntityListener.onToken(token1);

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId1, 2L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined

        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);

        completeFileAndCommit();
        assertEquals(2, nftRepository.count());

        Nft nft1Burn = getNft(tokenId1, 1L, EntityId.EMPTY, null, true, null, 5L); // mint/burn
        Nft nft1BurnTransfer = getNft(tokenId1, 1L, null, null, null, null, 5L); // mint/burn transfer
        sqlEntityListener.onNft(nft1Burn);
        sqlEntityListener.onNft(nft1BurnTransfer);

        Nft nft2Burn = getNft(tokenId1, 2L, EntityId.EMPTY, null, true, null, 6L); // mint/burn
        Nft nft2BurnTransfer = getNft(tokenId1, 2L, null, null, null, null, 6L); // mint/burn transfer
        sqlEntityListener.onNft(nft2Burn);
        sqlEntityListener.onNft(nft2BurnTransfer);

        completeFileAndCommit();

        // expected nfts
        Nft nft1 = getNft(tokenId1, 1L, null, 3L, true, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId1, 2L, null, 4L, true, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftTransferOwnershipAndDeleteOutOfOrder() {
        // create token first
        EntityId tokenId1 = EntityId.of("0.0.1", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.2", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.3", ACCOUNT);
        EntityId treasury = EntityId.of("0.0.98", ACCOUNT);
        String metadata1 = "nft1";
        String metadata2 = "nft2";

        // save token entities first
        Token token1 = getToken(tokenId1, treasury, 1L, 1L);
        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        // create nfts
        Nft nft1Combined = getNft(tokenId1, 1L, accountId1, 3L, false, metadata1, 3L); // mint transfer combined
        Nft nft2Combined = getNft(tokenId1, 2L, accountId2, 4L, false, metadata2, 4L); // mint transfer combined

        sqlEntityListener.onNft(nft1Combined);
        sqlEntityListener.onNft(nft2Combined);

        // nft 1 burn w transfer coming first
        Nft nft1Burn = getNft(tokenId1, 1L, EntityId.EMPTY, null, true, null, 5L); // mint/burn
        Nft nft1BurnTransfer = getNft(tokenId1, 1L, null, null, null, null, 5L); // mint/burn transfer
        sqlEntityListener.onNft(nft1BurnTransfer);
        sqlEntityListener.onNft(nft1Burn);

        // nft 2 burn w transfer coming first
        Nft nft2Burn = getNft(tokenId1, 2L, EntityId.EMPTY, null, true, null, 6L); // mint/burn
        Nft nft2BurnTransfer = getNft(tokenId1, 2L, null, null, null, null, 6L); // mint/burn transfer
        sqlEntityListener.onNft(nft2BurnTransfer);
        sqlEntityListener.onNft(nft2Burn);
        completeFileAndCommit();

        // expected nfts
        Nft nft1 = getNft(tokenId1, 1L, null, 3L, true, metadata1, 5L); // transfer
        Nft nft2 = getNft(tokenId1, 2L, null, 4L, true, metadata2, 6L); // transfer

        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(nft1, nft2);
    }

    @Test
    void onNftTransfer() {
        NftTransfer nftTransfer1 = getNftTransfer(1L, "0.0.1", 1L, "0.0.2", "0.0.3");
        NftTransfer nftTransfer2 = getNftTransfer(2L, "0.0.1", 2L, "0.0.2", "0.0.3");
        NftTransfer nftTransfer3 = getNftTransfer(3L, "0.0.2", 1L, "0.0.2", "0.0.3");

        // when
        sqlEntityListener.onNftTransfer(nftTransfer1);
        sqlEntityListener.onNftTransfer(nftTransfer2);
        sqlEntityListener.onNftTransfer(nftTransfer3);
        completeFileAndCommit();

        // then
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(nftTransfer1, nftTransfer2, nftTransfer3);
    }

    @Test
    void onToken() {
        Token token1 = getToken(EntityId.of("0.0.3", TOKEN), EntityId.of("0.0.5", ACCOUNT), 1L, 1L);
        Token token2 = getToken(EntityId.of("0.0.7", TOKEN), EntityId.of("0.0.11", ACCOUNT), 2L, 2L);

        // when
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        // then
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(token1, token2);
    }

    @Test
    void onTokenMerge() {
        EntityId tokenId = EntityId.of("0.0.3", TOKEN);
        EntityId accountId = EntityId.of("0.0.500", ACCOUNT);

        // save token entities first
        Token token = getToken(tokenId, accountId, 1L, 1L, 1000, false, keyFromString(KEY),
                1_000_000_000L, null, "FOO COIN TOKEN", null, "FOOTOK", null, null,
                TokenPauseStatusEnum.UNPAUSED);
        sqlEntityListener.onToken(token);

        Token tokenUpdated = getToken(tokenId, accountId, null, 5L, null, null, null,
                null, keyFromString(KEY2), "BAR COIN TOKEN", keyFromString(KEY), "BARTOK", keyFromString(KEY2),
                keyFromString(KEY2), TokenPauseStatusEnum.UNPAUSED);
        sqlEntityListener.onToken(tokenUpdated);
        completeFileAndCommit();

        // then
        Token tokenMerged = getToken(tokenId, accountId, 1L, 5L, 1000, false, keyFromString(KEY),
                1_000_000_000L, keyFromString(KEY2), "BAR COIN TOKEN", keyFromString(KEY), "BARTOK",
                keyFromString(KEY2), keyFromString(KEY2), TokenPauseStatusEnum.UNPAUSED);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(tokenMerged);
    }

    @Test
    void onTokenConsecutiveNegativeTotalSupply() {
        // given
        EntityId tokenId = EntityId.of("0.0.3", TOKEN);
        EntityId accountId = EntityId.of("0.0.500", ACCOUNT);

        // save token
        Token token = getToken(tokenId, accountId, 1L, 1L);
        sqlEntityListener.onToken(token);
        completeFileAndCommit();

        // when
        // two dissociate of the deleted token, both with negative amount
        Token update = getTokenUpdate(tokenId, 5);
        update.setTotalSupply(-10L);
        sqlEntityListener.onToken(update);

        update = getTokenUpdate(tokenId, 6);
        update.setTotalSupply(-15L);
        sqlEntityListener.onToken(update);

        completeFileAndCommit();

        // then
        token.setTotalSupply(token.getTotalSupply() - 25);
        token.setModifiedTimestamp(6);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(token);
    }

    @Test
    void onTokenMergeNegativeTotalSupply() {
        // given
        EntityId tokenId = EntityId.of("0.0.3", TOKEN);
        EntityId accountId = EntityId.of("0.0.500", ACCOUNT);

        // when
        // create token
        Token token = getToken(tokenId, accountId, 1L, 1L);
        sqlEntityListener.onToken(token);

        // token dissociate of the deleted token
        Token update = getTokenUpdate(tokenId, 5);
        update.setTotalSupply(-10L);
        sqlEntityListener.onToken(update);

        completeFileAndCommit();

        // then
        Token expected = getToken(tokenId, accountId, 1L, 5L);
        expected.setTotalSupply(expected.getTotalSupply() - 10);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(expected);
    }

    @Test
    void onTokenAccount() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.5", TOKEN);

        // save token entities first
        Token token1 = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        Token token2 = getToken(tokenId2, EntityId.of("0.0.110", ACCOUNT), 2L, 2L);
        sqlEntityListener.onToken(token1);
        sqlEntityListener.onToken(token2);
        completeFileAndCommit();

        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.11", ACCOUNT);
        TokenAccount tokenAccount1 = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        TokenAccount tokenAccount2 = getTokenAccount(tokenId2, accountId2, 6L, 6L, true, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);

        // when
        sqlEntityListener.onTokenAccount(tokenAccount1);
        sqlEntityListener.onTokenAccount(tokenAccount2);
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(tokenAccount1, tokenAccount2);
    }

    @Test
    void onTokenAccountDissociate() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token1 = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        sqlEntityListener.onToken(token1);
        completeFileAndCommit();

        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        TokenAccount dissociate = getTokenAccount(tokenId1, accountId1, null, 10L, false, null, null, null);

        // when
        sqlEntityListener.onTokenAccount(associate);
        sqlEntityListener.onTokenAccount(dissociate);
        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrder(
                associate,
                getTokenAccount(tokenId1, accountId1, 5L, 10L, false, false, TokenFreezeStatusEnum.NOT_APPLICABLE,
                        TokenKycStatusEnum.NOT_APPLICABLE)
        );
    }

    @Test
    void onTokenAccountMerge() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        sqlEntityListener.onToken(token);

        // when
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount tokenAccountAssociate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false, null, null);
        sqlEntityListener.onTokenAccount(tokenAccountAssociate);

        TokenAccount tokenAccountKyc = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(tokenAccountKyc);

        completeFileAndCommit();

        // then
        TokenAccount tokenAccountMerged = getTokenAccount(tokenId1, accountId1, 5L, 15L, true, false,
                TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.GRANTED);
        assertThat(tokenAccountRepository.findAll()).hasSize(2).contains(tokenAccountMerged);
    }

    @Test
    void onTokenAccountReassociate() {
        List<TokenAccount> expected = new ArrayList<>();
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);

        // save token entities first
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        tokenRepository.save(token);

        // token account was associated before this record file
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false,
                TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        tokenAccountRepository.save(associate);
        expected.add(associate);

        // when
        TokenAccount freeze = getTokenAccount(tokenId1, accountId1, null, 10L, null, null,
                TokenFreezeStatusEnum.FROZEN, null);
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 10L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED));

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 15L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        TokenAccount dissociate = getTokenAccount(tokenId1, accountId1, null, 16L, false, null, null, null);
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 16L, false, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        // associate after dissociate, the token has freeze key with freezeDefault = false, the token also has kyc key,
        // so the new relationship should have UNFROZEN, REVOKED
        TokenAccount reassociate = getTokenAccount(tokenId1, accountId1, 20L, 20L, true, false, null, null);
        sqlEntityListener.onTokenAccount(reassociate);
        expected.add(getTokenAccount(tokenId1, accountId1, 20L, 20L, true, false, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED));

        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenAccountMissingToken() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);

        // given no token row in db

        // when
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 10L, 10L, true, false, null, null);
        sqlEntityListener.onTokenAccount(associate);

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.count()).isZero();
    }

    @Test
    void onTokenAccountMissingLastAssociation() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);

        // given token in db and missing last account token association
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        tokenRepository.save(token);

        // when
        TokenAccount freeze = getTokenAccount(tokenId1, accountId1, null, 10L, null, null,
                TokenFreezeStatusEnum.FROZEN, null);
        sqlEntityListener.onTokenAccount(freeze);

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 15L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);

        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.count()).isZero();
    }

    @Test
    void onTokenAccountSpanningRecordFiles() {
        List<TokenAccount> expected = new ArrayList<>();
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.7", ACCOUNT);

        // given token in db
        Token token = getToken(tokenId1, EntityId.of("0.0.500", ACCOUNT), 1L, 1L);
        tokenRepository.save(token);

        // given association in a previous record file
        TokenAccount associate = getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false, null, null);
        sqlEntityListener.onTokenAccount(associate);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 5L, true, false, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED));

        completeFileAndCommit();

        // when in the next record file we have freeze, kycGrant, dissociate, associate, kycGrant
        TokenAccount freeze = getTokenAccount(tokenId1, accountId1, null, 10L, null, null, TokenFreezeStatusEnum.FROZEN,
                null);
        sqlEntityListener.onTokenAccount(freeze);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 10L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED));

        TokenAccount kycGrant = getTokenAccount(tokenId1, accountId1, null, 12L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 12L, true, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        TokenAccount dissociate = getTokenAccount(tokenId1, accountId1, null, 15L, false, null, null, null);
        sqlEntityListener.onTokenAccount(dissociate);
        expected.add(getTokenAccount(tokenId1, accountId1, 5L, 15L, false, false, TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.GRANTED));

        associate = getTokenAccount(tokenId1, accountId1, 20L, 20L, true, true, null, null);
        sqlEntityListener.onTokenAccount(associate);
        expected.add(getTokenAccount(tokenId1, accountId1, 20L, 20L, true, true, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.REVOKED));

        kycGrant = getTokenAccount(tokenId1, accountId1, null, 22L, null, null, null,
                TokenKycStatusEnum.GRANTED);
        sqlEntityListener.onTokenAccount(kycGrant);
        expected.add(getTokenAccount(tokenId1, accountId1, 20L, 22L, true, true, TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED));

        completeFileAndCommit();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void onTokenAllowance() {
        // given
        TokenAllowance tokenAllowance1 = domainBuilder.tokenAllowance().get();
        TokenAllowance tokenAllowance2 = domainBuilder.tokenAllowance().get();

        // when
        sqlEntityListener.onTokenAllowance(tokenAllowance1);
        sqlEntityListener.onTokenAllowance(tokenAllowance2);
        completeFileAndCommit();

        // then
        assertThat(entityRepository.count()).isZero();
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrder(tokenAllowance1, tokenAllowance2);
        assertThat(findHistory(TokenAllowance.class, "payer_account_id, spender, token_id")).isEmpty();
    }

    @ValueSource(ints = {1, 2, 3})
    @ParameterizedTest
    void onTokenAllowanceHistory(int commitIndex) {
        // given
        final String idColumns = "payer_account_id, spender, token_id";
        var builder = domainBuilder.tokenAllowance();
        TokenAllowance tokenAllowanceCreate = builder.get();

        TokenAllowance tokenAllowanceUpdate1 = builder.customize(c -> c.amount(999L)).get();
        tokenAllowanceUpdate1.setTimestampLower(tokenAllowanceCreate.getTimestampLower() + 1);

        TokenAllowance tokenAllowanceUpdate2 = builder.customize(c -> c.amount(0)).get();
        tokenAllowanceUpdate2.setTimestampLower(tokenAllowanceCreate.getTimestampLower() + 2);

        // Expected merged objects
        TokenAllowance mergedCreate = TestUtils.clone(tokenAllowanceCreate);
        TokenAllowance mergedUpdate1 = TestUtils.merge(tokenAllowanceCreate, tokenAllowanceUpdate1);
        TokenAllowance mergedUpdate2 = TestUtils.merge(mergedUpdate1, tokenAllowanceUpdate2);
        mergedCreate.setTimestampUpper(tokenAllowanceUpdate1.getTimestampLower());

        // when
        sqlEntityListener.onTokenAllowance(tokenAllowanceCreate);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowanceCreate);
            assertThat(findHistory(TokenAllowance.class, idColumns)).isEmpty();
        }

        sqlEntityListener.onTokenAllowance(tokenAllowanceUpdate1);
        if (commitIndex > 2) {
            completeFileAndCommit();
            assertThat(tokenAllowanceRepository.findAll()).containsExactly(mergedUpdate1);
            assertThat(findHistory(TokenAllowance.class, idColumns)).containsExactly(mergedCreate);
        }

        sqlEntityListener.onTokenAllowance(tokenAllowanceUpdate2);
        completeFileAndCommit();

        // then
        mergedUpdate1.setTimestampUpper(tokenAllowanceUpdate2.getTimestampLower());
        assertThat(tokenAllowanceRepository.findAll()).containsExactly(mergedUpdate2);
        assertThat(findHistory(TokenAllowance.class, idColumns)).containsExactly(mergedCreate, mergedUpdate1);
    }

    @Test
    void onTokenTransfer() {
        EntityId tokenId1 = EntityId.of("0.0.3", TOKEN);
        EntityId tokenId2 = EntityId.of("0.0.7", TOKEN);
        EntityId accountId1 = EntityId.of("0.0.5", ACCOUNT);
        EntityId accountId2 = EntityId.of("0.0.9", ACCOUNT);

        TokenTransfer tokenTransfer1 = getTokenTransfer(1000, 2L, tokenId1, accountId1);
        TokenTransfer tokenTransfer2 = getTokenTransfer(50, 2L, tokenId2, accountId2);
        TokenTransfer tokenTransfer3 = getTokenTransfer(-444, 4L, tokenId1, accountId1);

        // when
        sqlEntityListener.onTokenTransfer(tokenTransfer1);
        sqlEntityListener.onTokenTransfer(tokenTransfer2);
        sqlEntityListener.onTokenTransfer(tokenTransfer3);
        completeFileAndCommit();

        // then
        assertThat(tokenTransferRepository.findAll())
                .containsExactlyInAnyOrder(tokenTransfer1, tokenTransfer2, tokenTransfer3);
    }

    @ValueSource(ints = {1, 2})
    @ParameterizedTest
    void onSchedule(int commitIndex) {
        var schedule = domainBuilder.schedule().get();
        var expected = TestUtils.clone(schedule);

        sqlEntityListener.onSchedule(schedule);
        if (commitIndex > 1) {
            completeFileAndCommit();
            assertThat(scheduleRepository.findAll()).containsOnly(expected);
        }

        var scheduleUpdate = new Schedule();
        scheduleUpdate.setExecutedTimestamp(domainBuilder.timestamp());
        scheduleUpdate.setScheduleId(schedule.getScheduleId());
        expected.setExecutedTimestamp(scheduleUpdate.getExecutedTimestamp());

        sqlEntityListener.onSchedule(scheduleUpdate);
        completeFileAndCommit();

        assertThat(scheduleRepository.findAll()).containsOnly(expected);
    }

    @Test
    void onScheduleExecutedWithoutScheduleCreate() {
        // For partial mirrornode which can miss a schedulecreate tx for an executed scheduled tx
        var schedule = new Schedule();
        schedule.setExecutedTimestamp(domainBuilder.timestamp());
        schedule.setScheduleId(domainBuilder.entityId(SCHEDULE).getId());
        sqlEntityListener.onSchedule(schedule);
        assertThat(scheduleRepository.findAll()).isEmpty();
    }

    @Test
    void onScheduleSignature() {
        TransactionSignature transactionSignature1 = domainBuilder.transactionSignature().get();
        TransactionSignature transactionSignature2 = domainBuilder.transactionSignature().get();
        TransactionSignature transactionSignature3 = domainBuilder.transactionSignature().get();

        // when
        sqlEntityListener.onTransactionSignature(transactionSignature1);
        sqlEntityListener.onTransactionSignature(transactionSignature2);
        sqlEntityListener.onTransactionSignature(transactionSignature3);
        completeFileAndCommit();

        // then
        assertThat(transactionSignatureRepository.findAll())
                .containsExactlyInAnyOrder(transactionSignature1, transactionSignature2, transactionSignature3);
    }

    @Test
    void onScheduleMerge() {
        Schedule schedule = domainBuilder.schedule().get();
        sqlEntityListener.onSchedule(schedule);

        Schedule scheduleUpdated = new Schedule();
        scheduleUpdated.setScheduleId(schedule.getScheduleId());
        scheduleUpdated.setExecutedTimestamp(5L);
        sqlEntityListener.onSchedule(scheduleUpdated);

        // when
        completeFileAndCommit();

        // then
        schedule.setExecutedTimestamp(5L);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrder(schedule);
    }

    @Test
    void onEthereumTransaction() {
        var ethereumTransaction = domainBuilder.ethereumTransaction().get();
        sqlEntityListener.onEthereumTransaction(ethereumTransaction);

        // when
        completeFileAndCommit();

        // then
        assertThat(ethereumTransactionRepository.findAll()).contains(ethereumTransaction);
    }

    private void completeFileAndCommit() {
        RecordFile recordFile = domainBuilder.recordFile().persist();
        transactionTemplate.executeWithoutResult(status -> sqlEntityListener.onEnd(recordFile));
        assertThat(recordFileRepository.findAll()).contains(recordFile);
    }

    private Entity getEntity(long id, long modifiedTimestamp) {
        return getEntity(id, null, modifiedTimestamp, null, null, null);
    }

    private Entity getEntity(long id, Long createdTimestamp, long modifiedTimestamp,
                             Integer maxAutomaticTokenAssociations, String memo, Key adminKey) {
        return getEntity(id, createdTimestamp, modifiedTimestamp, memo, adminKey, null, null, null, null,
                maxAutomaticTokenAssociations, false, null);
    }

    private Entity getEntity(long id, Long createdTimestamp, long modifiedTimestamp, String memo,
                             Key adminKey, EntityId autoRenewAccountId, Long autoRenewPeriod,
                             Boolean deleted, Long expiryTimeNs, Integer maxAutomaticTokenAssociations,
                             Boolean receiverSigRequired, Key submitKey) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setAutoRenewAccountId(autoRenewAccountId);
        entity.setAutoRenewPeriod(autoRenewPeriod);
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setDeleted(deleted);
        entity.setExpirationTimestamp(expiryTimeNs);
        entity.setKey(adminKey != null ? adminKey.toByteArray() : null);
        entity.setMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations);
        entity.setTimestampLower(modifiedTimestamp);
        entity.setNum(id);
        entity.setRealm(0L);
        entity.setReceiverSigRequired(receiverSigRequired);
        entity.setShard(0L);
        entity.setSubmitKey(submitKey != null ? submitKey.toByteArray() : null);
        entity.setType(ACCOUNT);
        if (memo != null) {
            entity.setMemo(memo);
        }
        return entity;
    }

    private TopicMessage getTopicMessage() {
        TopicMessage topicMessage = new TopicMessage();
        topicMessage.setChunkNum(1);
        topicMessage.setChunkTotal(2);
        topicMessage.setConsensusTimestamp(1L);
        topicMessage.setMessage("test message".getBytes());
        topicMessage.setPayerAccountId(EntityId.of("0.1.1000", EntityType.ACCOUNT));
        topicMessage.setRunningHash("running hash".getBytes());
        topicMessage.setRunningHashVersion(2);
        topicMessage.setSequenceNumber(1L);
        topicMessage.setTopicId(EntityId.of("0.0.1001", EntityType.TOPIC));
        topicMessage.setValidStartTimestamp(4L);

        return topicMessage;
    }

    @SneakyThrows
    private Token getToken(EntityId tokenId, EntityId accountId, Long createdTimestamp, long modifiedTimestamp) {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var hexKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(instr))).build();
        return getToken(tokenId, accountId, createdTimestamp, modifiedTimestamp, 1000, false, hexKey,
                1_000_000_000L, hexKey, "FOO COIN TOKEN", hexKey, "FOOTOK", hexKey, hexKey,
                TokenPauseStatusEnum.UNPAUSED);
    }

    private Token getToken(EntityId tokenId, EntityId accountId, Long createdTimestamp, long modifiedTimestamp,
                           Integer decimals, Boolean freezeDefault, Key freezeKey, Long initialSupply, Key kycKey,
                           String name, Key supplyKey, String symbol, Key wipeKey, Key pauseKey,
                           TokenPauseStatusEnum pauseStatus) {
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(decimals);
        token.setFreezeDefault(freezeDefault);
        token.setFreezeKey(freezeKey != null ? freezeKey.toByteArray() : null);
        token.setInitialSupply(initialSupply);
        token.setKycKey(kycKey != null ? kycKey.toByteArray() : null);
        token.setMaxSupply(0L);
        token.setModifiedTimestamp(modifiedTimestamp);
        token.setName(name);
        token.setPauseKey(pauseKey != null ? pauseKey.toByteArray() : null);
        token.setPauseStatus(pauseStatus);
        token.setSupplyKey(supplyKey != null ? supplyKey.toByteArray() : null);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol(symbol);
        token.setTokenId(new TokenId(tokenId));
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setTreasuryAccountId(accountId);
        token.setWipeKey(wipeKey != null ? wipeKey.toByteArray() : null);

        return token;
    }

    private Token getTokenUpdate(EntityId tokenId, long modifiedTimestamp) {
        Token token = Token.of(tokenId);
        token.setModifiedTimestamp(modifiedTimestamp);
        return token;
    }

    private Nft getNft(EntityId tokenId, long serialNumber, EntityId accountId, Long createdTimestamp, Boolean deleted,
                       String metadata, long modifiedTimestamp) {
        Nft nft = new Nft();
        nft.setAccountId(accountId);
        nft.setCreatedTimestamp(createdTimestamp);
        nft.setDeleted(deleted);
        nft.setMetadata(metadata == null ? null : metadata.getBytes(StandardCharsets.UTF_8));
        nft.setId(new NftId(serialNumber, tokenId));
        nft.setModifiedTimestamp(modifiedTimestamp);

        return nft;
    }

    private NftTransfer getNftTransfer(long consensusTimestamp, String tokenId, long serialNumber, String receiverId,
                                       String senderId) {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(consensusTimestamp, serialNumber, EntityId.of(tokenId, TOKEN)));
        nftTransfer.setReceiverAccountId(EntityId.of(receiverId, ACCOUNT));
        nftTransfer.setSenderAccountId(EntityId.of(senderId, ACCOUNT));
        nftTransfer.setPayerAccountId(TRANSACTION_PAYER);
        return nftTransfer;
    }

    private TokenAccount getTokenAccount(EntityId tokenId, EntityId accountId, Long createdTimestamp,
                                         long modifiedTimeStamp, Boolean associated, Boolean autoAssociated,
                                         TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, modifiedTimeStamp);
        tokenAccount.setAssociated(associated);
        tokenAccount.setAutomaticAssociation(autoAssociated);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        return tokenAccount;
    }

    private TokenTransfer getTokenTransfer(long amount, long consensusTimestamp, EntityId tokenId, EntityId accountId) {
        TokenTransfer tokenTransfer = new TokenTransfer();
        tokenTransfer.setAmount(amount);
        tokenTransfer.setId(new TokenTransfer.Id(consensusTimestamp, tokenId, accountId));
        tokenTransfer.setPayerAccountId(TRANSACTION_PAYER);
        return tokenTransfer;
    }
}

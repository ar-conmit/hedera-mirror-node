package com.hedera.mirror.common.domain.transaction;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.ByteArrayWeiBarConverter;
import com.hedera.mirror.common.converter.ByteArrayWeiBarToStringSerializer;
import com.hedera.mirror.common.converter.EntityIdSerializer;
import com.hedera.mirror.common.converter.LongWeiBarConverter;
import com.hedera.mirror.common.converter.LongWeiBarToStringSerializer;
import com.hedera.mirror.common.domain.entity.EntityId;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Builder
@Data
@Entity
@NoArgsConstructor
public class EthereumTransaction implements Persistable<Long> {

    @ToString.Exclude
    private byte[] accessList;

    @ToString.Exclude
    private byte[] callData;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId callDataId;

    @ToString.Exclude
    private byte[] chainId;

    @Id
    private Long consensusTimestamp;

    @ToString.Exclude
    private byte[] data;

    @ToString.Exclude
    private byte[] fromAddress;

    // set in weibar, persisted in tinybar
    @Convert(converter = LongWeiBarConverter.class)
    @JsonSerialize(using = LongWeiBarToStringSerializer.class)
    private Long gasLimit;

    // set in weibar, persisted in tinybar
    @Convert(converter = ByteArrayWeiBarConverter.class)
    @JsonSerialize(using = ByteArrayWeiBarToStringSerializer.class)
    private byte[] gasPrice;

    @ToString.Exclude
    private byte[] hash;

    // set in weibar, persisted in tinybar
    @Convert(converter = ByteArrayWeiBarConverter.class)
    @JsonSerialize(using = ByteArrayWeiBarToStringSerializer.class)
    private byte[] maxFeePerGas;

    // set in weibar, persisted in tinybar
    @Convert(converter = LongWeiBarConverter.class)
    @JsonSerialize(using = LongWeiBarToStringSerializer.class)
    private Long maxGasAllowance;

    // set in weibar, persisted in tinybar
    @Convert(converter = ByteArrayWeiBarConverter.class)
    @JsonSerialize(using = ByteArrayWeiBarToStringSerializer.class)
    private byte[] maxPriorityFeePerGas;

    private Long nonce;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId payerAccountId;

    private Integer recoveryId;

    @Column(name = "signature_r")
    @ToString.Exclude
    private byte[] signatureR;

    @Column(name = "signature_s")
    @ToString.Exclude
    private byte[] signatureS;

    @Column(name = "signature_v")
    @ToString.Exclude
    private byte[] signatureV;

    @ToString.Exclude
    private byte[] toAddress;

    private Integer type;

    @ToString.Exclude
    private byte[] value;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }
}

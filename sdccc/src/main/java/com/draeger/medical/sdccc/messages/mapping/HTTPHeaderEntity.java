/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2022 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.messages.mapping;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

/**
 * POJO for persisting headers.
 */
@Entity(name = "HTTPHeaderEntity")
@Table(name = "http_header_entity")
public class HTTPHeaderEntity {

    @Id
    @GenericGenerator(name = "HTTPHeaderIDGen", strategy = "increment")
    @GeneratedValue(generator = "HTTPHeaderIDGen")
    private long incId;

    private String entryKey;

    @ManyToOne(fetch = FetchType.LAZY)
    private MessageContent messageContent;

    private String entryValue;

    /**
     * This will be used by hibernate when creating the POJO from database entries.
     */
    public HTTPHeaderEntity() {}

    /**
     * This will be used when creating the POJO before loading it into the database.
     *
     * @param entryKey       the key string
     * @param entryValue     the string the key gets mapped to
     * @param messageContent row to link to
     */
    public HTTPHeaderEntity(final String entryKey, final String entryValue, final MessageContent messageContent) {
        this.entryKey = entryKey;
        this.entryValue = entryValue;
        this.messageContent = messageContent;
    }

    public String getEntryKey() {
        return entryKey;
    }

    public String getEntryValue() {
        return entryValue;
    }
}

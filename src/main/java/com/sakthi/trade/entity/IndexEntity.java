package com.sakthi.trade.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Getter
@Setter
@Entity(name = "Index")
public class IndexEntity {

    @Id
    @Column(name="index_key", nullable =false)
    String indexKey;
    @Column(name="index_name", nullable =false)
    String indexName;
}


package com.exem.dust.dao;

import com.exem.dust.domain.reject.ConnectionReject;

import java.util.List;

public interface RejectDao {

    void insert(ConnectionReject reject);

    List<ConnectionReject> findRecent(int limit);
}

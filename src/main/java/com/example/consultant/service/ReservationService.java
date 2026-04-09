package com.example.consultant.service;



import com.example.consultant.mapper.ReservationMapper;
import com.example.consultant.pojo.Reservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 预约信息服务。
 * <p>
 * 负责预约记录的新增和按手机号查询，是 `reservationTool` 的底层实现。
 * </p>
 */
@Service
public class ReservationService {
    @Autowired
    private ReservationMapper reservationMapper;

    /**
     * 新增预约记录。
     */
    public void insert(Reservation reservation) {
        reservationMapper.insert(reservation);
    }

    /**
     * 根据手机号查询预约信息。
     */
    public Reservation findByPhone(String phone) {
        return reservationMapper.findByPhone(phone);
    }
}

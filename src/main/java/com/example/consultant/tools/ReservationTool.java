package com.example.consultant.tools;

import com.example.consultant.pojo.Reservation;
import com.example.consultant.service.ReservationService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ReservationTool {

    private final ReservationService reservationService;

    public ReservationTool(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Tool("新增预约填报信息")
    public void addReservation(@P("考生姓名") String name,
                               @P("考生手机号") String phone,
                               @P("考生性别") String gender,
                               @P("语音沟通时间，格式为 yyyy-MM-dd'T'HH:mm") String communicationTime,
                               @P("考生所在省份") String province,
                               @P("考生预估分数") Integer estimateScore) {
        Reservation reservation = new Reservation(
                null,
                name,
                gender,
                phone,
                LocalDateTime.parse(communicationTime),
                province,
                estimateScore
        );
        reservationService.insert(reservation);
    }

    @Tool("根据手机号查询预约信息")
    public Reservation findByPhone(@P("考生手机号") String phone) {
        return reservationService.findByPhone(phone);
    }
}

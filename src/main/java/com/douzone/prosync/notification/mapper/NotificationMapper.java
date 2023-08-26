package com.douzone.prosync.notification.mapper;

import com.douzone.prosync.notification.dto.NotificationDto;
import com.douzone.prosync.notification.dto.NotificationTargetDto;
import com.douzone.prosync.notification.dto.response.NotificationResponse;
import com.douzone.prosync.notification.entity.Notification;
import com.douzone.prosync.notification.entity.NotificationTarget;
import com.douzone.prosync.searchcondition.NotificationSearchCondition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface NotificationMapper {
    void saveNotification(NotificationDto notificationDto);

    void saveNotificationTarget(NotificationTargetDto notificationDto);

    void saveNotificationTargetList(List<NotificationTargetDto> dtoList);

    void updateIsRead(@Param("isRead") boolean isRead,@Param("targetId") Long targetId);
    void updateIsTransmittedbyTagetId(@Param("isTransmitted") boolean isTransmitted,@Param("targetId") Long targetId);

    void updateIsTransmittedbyMemberId(@Param("isTransmitted") boolean isTransmitted,@Param("memberId") Long memberId);

    void update(NotificationDto notificationDto);

    void deleteNotificationTarget(Long targetId);

    void deleteNotification(Long notificationId);

    Notification findById(Long notificationId);

    List<NotificationResponse> getNotificationList(@Param("condition") NotificationSearchCondition condition);
    NotificationTarget findTargetById(Long targetId);

    Integer getNotificationListCount(Long memberId);

    List<NotificationTarget> getNotificationTagetListByNotificationId(Long notificationId);
}

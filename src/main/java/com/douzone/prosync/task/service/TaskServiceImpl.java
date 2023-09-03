package com.douzone.prosync.task.service;

import com.douzone.prosync.common.PageResponseDto;
import com.douzone.prosync.exception.ApplicationException;
import com.douzone.prosync.exception.ErrorCode;
import com.douzone.prosync.file.dto.FileRequestDto;
import com.douzone.prosync.file.entity.FileInfo;
import com.douzone.prosync.file.service.FileService;
import com.douzone.prosync.log.dto.LogConditionDto;
import com.douzone.prosync.log.logenum.LogCode;
import com.douzone.prosync.log.service.LogServiceImpl;
import com.douzone.prosync.member_project.repository.MemberProjectMapper;
import com.douzone.prosync.notification.dto.NotificationConditionDto;
import com.douzone.prosync.notification.notienum.NotificationCode;
import com.douzone.prosync.notification.service.NotificationService;
import com.douzone.prosync.project.entity.Project;
import com.douzone.prosync.project.service.ProjectService;
import com.douzone.prosync.task.dto.request.TaskMemberResponseDto;
import com.douzone.prosync.task.dto.request.TaskPatchDto;
import com.douzone.prosync.task.dto.request.TaskPostDto;
import com.douzone.prosync.task.dto.response.GetTaskResponse;
import com.douzone.prosync.task.dto.response.GetTasksResponse;
import com.douzone.prosync.task.repository.TaskMapper;
import com.douzone.prosync.task_status.service.TaskStatusService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@Transactional
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final TaskStatusService taskStatusService;
    private final ProjectService projectService;

    private final LogServiceImpl logService;

    private final MemberProjectMapper memberProjectMapper;

    private final NotificationService notificationService;
    private final FileService fileService;


    @Override
    public Long createTask(TaskPostDto dto, Long projectId, Long memberId) {
        Project findProject = projectService.findProject(projectId);

        // check task_status of project
        verifyTaskStatus(findProject.getProjectId(), dto.getTaskStatusId(), memberId);

        taskMapper.save(dto, projectId);
        Long taskId = dto.getTaskId();

        //TODO : 조건 수정
        if (!dto.getFileIds().isEmpty()) {
            List<FileInfo> fileInfos = FileInfo.createFileInfos(dto.getFileIds(), FileInfo.FileTableName.TASK, taskId);
            fileService.saveFileInfoList(fileInfos);
        }

        return taskId;
    }

    @Override
    public void updateTask(TaskPatchDto dto, Long taskId, Long memberId) {

        GetTaskResponse findTask = findExistTask(taskId);
        dto.setTaskId(taskId);

        // find task_status of project
        if (Optional.ofNullable(dto.getTaskStatusId()).isPresent()) {
            verifyTaskStatus(findTask.getProjectId(), dto.getTaskStatusId(), memberId);
        }
        //TODO : 파일 수정 추가

        taskMapper.update(dto);
    }

    @Override
    public void deleteTask(Long taskId, Long memberId) {
        GetTaskResponse task = verifyExistTask(taskId);
        //soft delete
        taskMapper.delete(taskId);


        // Todo: 해당 Task의 멤버들에게 알림을 전달하는 로직 작성
        List<TaskMemberResponseDto> taskMembers = taskMapper.findTaskMembers(taskId);
        List<Long> memberIds  = taskMembers.stream()
                .map(TaskMemberResponseDto::getMemberId)
                .collect(Collectors.toList());

        // 알림 전달
        notificationService.saveAndSendNotification(NotificationConditionDto.builder()
                .fromMemberId(memberId)
                .code(NotificationCode.TASK_REMOVE)
                .memberIds(memberIds)
                .subject(task)
                .build());

        // 로그 전달
        logService.saveLog(LogConditionDto.builder()
                .fromMemberId(memberId)
                .code(LogCode.TASK_REMOVE)
                .projectId(task.getProjectId())
                .subject(task).build());

        fileService.deleteFileList(FileRequestDto.create(FileInfo.FileTableName.TASK, taskId));

    }

    @Transactional(readOnly = true)
    @Override
    public GetTaskResponse findTask(Long taskId, Long memberId) {
        return findExistTask(taskId);
    }

    /**
     * 업무 리스트 조회
     */
    @Override
    public PageResponseDto<GetTasksResponse.PerTasksResponse> findTaskList(Long projectId, Pageable pageable, String search, boolean isActive, String view, String status, Long memberId) {
        int pageNum = pageable.getPageNumber() == 0 ? 1 : pageable.getPageNumber();
        PageHelper.startPage(pageNum, pageable.getPageSize());

        List<GetTasksResponse> tasks = taskMapper.findTasks(projectId, search, isActive);

        // task member 세팅
        tasks.forEach(task -> {
            task.setTaskMembers(taskMapper.findTaskMembers(task.getTaskId()));
        });

        // 필터 - task status
        if (status != null && !status.trim().isEmpty()) {
            tasks = tasks.stream().filter(task -> task.getTaskStatus().equalsIgnoreCase(status)).collect(Collectors.toList());
        }

        // 보드뷰일 경우 task_status별 응답 리턴
        if (view != null && view.equals("board")) {
            List<GetTasksResponse.PerTasksResponse> list = GetTasksResponse.PerTasksResponse.of(tasks);
            return new PageResponseDto<>(new PageInfo<>(list));
        }
        return new PageResponseDto(new PageInfo<>(tasks));
    }

    /**
     * 업무 담당자 지정
     */
    @Override
    public void createTaskMember(Long taskId, List<Long> projectMemberIds, Long memberId) {

        GetTaskResponse task = verifyExistTask(taskId);

        // 이미 담당자로 지정되어있는 경우 경우
        List<TaskMemberResponseDto> taskMembers = taskMapper.findTaskMembers(taskId);
        taskMembers.forEach(taskMember -> {
            if (projectMemberIds.contains(taskMember.getMemberProjectId())) {
                throw new ApplicationException(ErrorCode.TASK_MEMBER_EXISTS);
            }
        });
        // TODO : projectMemberId 값이 프로젝트 회원 아닌 경우 예외

        taskMapper.saveTaskMember(taskId, projectMemberIds);
        List<Long> memberIds = memberProjectMapper.findMemberIdsListById(projectMemberIds);

        // 알림 전달
        notificationService.saveAndSendNotification(NotificationConditionDto.builder()
                .fromMemberId(memberId)
                .code(NotificationCode.TASK_ASSIGNMENT)
                .memberIds(memberIds)
                .taskId(task.getTaskId())
                .subject(task).build());

    }

    /**
     * 업무 담당자 삭제
     */
    @Override
    public void deleteTaskMember(Long taskId, List<Long> projectMemberIds, Long memberId) {
        GetTaskResponse task = verifyExistTask(taskId);

        taskMapper.deleteTaskMember(taskId, projectMemberIds);


    }

    @Override
    public List<TaskMemberResponseDto> findTaskMembers(Long taskId, long memberId) {
        return taskMapper.findTaskMembers(taskId);
    }

    private GetTaskResponse findExistTask(Long taskId) {
        return taskMapper.findById(taskId).orElseThrow(() -> new ApplicationException(ErrorCode.TASK_NOT_FOUND));
    }

    /**
     * 삭제된 task인 경우 예외 처리
     */
    private GetTaskResponse verifyExistTask(Long taskId) {

        GetTaskResponse task = taskMapper.findById(taskId).orElse(null);
        if (task == null) {
            throw new ApplicationException(ErrorCode.TASK_NOT_FOUND);
        }

        return task;
    }

    private void verifyTaskStatus(Long projectId, Long taskStatusId, Long memberId) {
        taskStatusService.getTaskStatusByProject(projectId, false, memberId)
                .stream()
                .filter(status -> status.getTaskStatusId() == taskStatusId).findFirst()
                .orElseThrow(() -> new ApplicationException(ErrorCode.TASK_STATUS_NOT_FOUND));
    }

}

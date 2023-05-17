package com.example.hanghaetinder_bemain.domain.chat.controller;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.joda.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.hanghaetinder_bemain.domain.chat.dto.response.ChatMessageListDto;
import com.example.hanghaetinder_bemain.domain.chat.dto.response.ChatRoomListDto;
import com.example.hanghaetinder_bemain.domain.chat.entity.ChatMessage;
import com.example.hanghaetinder_bemain.domain.chat.entity.ChatRoom;
import com.example.hanghaetinder_bemain.domain.member.entity.Member;
import com.example.hanghaetinder_bemain.domain.chat.repository.ChatMessageRepository;
import com.example.hanghaetinder_bemain.domain.chat.repository.ChatRoomRepository;
import com.example.hanghaetinder_bemain.domain.member.repository.MatchMemberRepository;
import com.example.hanghaetinder_bemain.domain.member.repository.MemberRepository;
import com.example.hanghaetinder_bemain.domain.member.util.Message;
import com.example.hanghaetinder_bemain.domain.member.util.StatusEnum;
import com.example.hanghaetinder_bemain.domain.security.UserDetailsImpl;
import com.example.hanghaetinder_bemain.domain.member.service.MemberService;
import com.example.hanghaetinder_bemain.domain.chat.service.ChatMessageService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class ChatController {

	private final SimpMessageSendingOperations messagingTemplate;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatMessageService chatMessageService;
	private final ChatRoomRepository chatRoomRepository;
	private final MatchMemberRepository matchMemberRepository;
	private final MemberRepository memberRepository;

	@Transactional
	@MessageMapping("/chat/message")
	public void message(ChatMessage message) {

		System.out.println("**********웹소켓 들어온다*********");
		switch (message.getType()){
			case ROOM:
				System.out.println("**********ROOM*********");
				System.out.println(message.getRoomId());
				Optional<Member> member = memberRepository.findByUserId(message.getRoomId());
				System.out.println(member.get().getUserId());
				List<ChatRoom> matchMemberOptional = matchMemberRepository.findMatchmember(member.get().getId());
				if (matchMemberOptional.size() != 0) {
					ChatRoomListDto chatRoomListDto = ChatRoomListDto.from(matchMemberOptional);
					Message msg = Message.setSuccess(StatusEnum.OK, "조회 성공", chatRoomListDto);
					messagingTemplate.convertAndSend("/sub/chat/rooms/" + message.getRoomId(), msg);
				}
				else{
					ChatRoomListDto chatRoomListDto = new ChatRoomListDto();
					Message msg = Message.setSuccess(StatusEnum.OK, "조회 성공", chatRoomListDto);
					messagingTemplate.convertAndSend("/sub/chat/rooms/" + message.getRoomId(), msg);
				}
				break;
			case ENTER:
				System.out.println("**********ENTER*********");
				Optional<ChatRoom> chatRoomOptional = chatRoomRepository.findByRoomId(message.getRoomId());
				if (chatRoomOptional.isPresent()) {
					Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").ascending());
					Page<ChatMessage> chatMessages = chatMessageRepository.findByRoomId(chatRoomOptional.get().getRoomId(), pageable);
					ChatMessageListDto chatMessageListDto = ChatMessageListDto.from(chatMessages);
					Message msg = Message.setSuccess(StatusEnum.OK, "조회 성공", chatMessageListDto);
					messagingTemplate.convertAndSend("/sub/chat/rooms/" + message.getUserId(), msg);
				}
				break;

			case TALK:
				System.out.println("**********TALK*********");
				Optional<Member> nickname = memberRepository.findByUserId(message.getUserId());
				ChatMessageListDto.ChatMessageDto messageDto = new ChatMessageListDto.ChatMessageDto(nickname.get().getUserId(), message.getMessage(), new Date());
				ChatRoom chatRoom = chatRoomRepository.findRoomId(message.getRoomId());
				ChatMessage chatMessage = new ChatMessage(message.getType(), message.getRoomId(), nickname.get().getUserId(), message.getMessage(), new Date(), chatRoom);
				messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), messageDto);
				chatMessageService.updateChatRoomListAsync(message);
				chatMessageService.save(chatMessage);
				break;

			default:
				break;
		}
		System.out.println("******메세지는" + message);

	}

	@GetMapping("/api/user/{Rid}/messages")
	public ResponseEntity<ChatMessageListDto> roomMessages(@PathVariable String Rid, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {

		Optional<ChatRoom> chatRoomOptional = chatRoomRepository.findByRoomId(Rid);
		if (chatRoomOptional.isPresent()) {
			Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
			Page<ChatMessage> chatMessages = chatMessageRepository.findByRoomId(chatRoomOptional.get().getRoomId(), pageable);

			ChatMessageListDto chatMessageListDto = ChatMessageListDto.from(chatMessages);
			return ResponseEntity.ok().body(chatMessageListDto);
		}
		return ResponseEntity.notFound().build();
	}

	@Transactional
	@GetMapping("/api/user/room")
	public ResponseEntity<Message> chatRooms(@AuthenticationPrincipal final UserDetailsImpl userDetails) {

		Optional<Member> member = memberRepository.findById(userDetails.getId());
		List<ChatRoom> matchMemberOptional = matchMemberRepository.findMatchmember(member.get().getId());
		if (matchMemberOptional.size() != 0) {
			ChatRoomListDto chatRoomListDto = ChatRoomListDto.from(matchMemberOptional);
			Message message = Message.setSuccess(StatusEnum.OK, "조회 성공", chatRoomListDto);
			return new ResponseEntity<>(message, HttpStatus.OK);
		}
		Message message = Message.setSuccess(StatusEnum.OK, "조회 결과 없음");
		return new ResponseEntity<>(message, HttpStatus.OK);
	}

	@GetMapping("/api/user/chat/{id}")
	public String roomDetail(@PathVariable Long id) {
		//websocket
		String RoomId = chatRoomRepository.findRoomnum(id).getRoomId();
		return RoomId;
	}

	@GetMapping("/api/user-info")
	@ResponseBody
	public Member getUserName(@AuthenticationPrincipal UserDetailsImpl userDetails) {
		return userDetails.getMember();
	}
}
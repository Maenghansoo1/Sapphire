package com.sapphire.hawe.withdraw.dao;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WithdrawMapper {
    @Select("SELECT group_id FROM group_members WHERE user_id = #{userId} ORDER BY group_id")
    List<Long> findGroupIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) > 0 FROM `groups` g WHERE g.owner_id = #{userId} "
            + "AND (SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id) > 1")
    boolean existsOwnedGroupWithOtherMembers(@Param("userId") Long userId);

    @Select("SELECT media_url FROM submissions WHERE user_id = #{userId} AND media_url IS NOT NULL")
    List<String> findSubmissionMediaKeys(@Param("userId") Long userId);

    @Select("SELECT c.image_url FROM comments c JOIN submissions s ON s.id = c.submission_id "
            + "WHERE s.user_id = #{userId} AND c.image_url IS NOT NULL")
    List<String> findCommentImageKeysOnMySubmissions(@Param("userId") Long userId);

    @Select("SELECT image_url FROM comments WHERE user_id = #{userId} AND image_url IS NOT NULL")
    List<String> findMyCommentImageKeys(@Param("userId") Long userId);

    @Delete("DELETE cm FROM comment_mentions cm JOIN comments c ON c.id = cm.comment_id "
            + "JOIN submissions s ON s.id = c.submission_id WHERE s.user_id = #{userId}")
    void deleteMentionsOnMySubmissions(@Param("userId") Long userId);

    @Delete("DELETE c FROM comments c JOIN submissions s ON s.id = c.submission_id WHERE s.user_id = #{userId}")
    void deleteCommentsOnMySubmissions(@Param("userId") Long userId);

    @Delete("DELETE r FROM reactions r JOIN submissions s ON s.id = r.submission_id WHERE s.user_id = #{userId}")
    void deleteReactionsOnMySubmissions(@Param("userId") Long userId);

    @Delete("DELETE sg FROM submission_guesses sg JOIN submissions s ON s.id = sg.submission_id "
            + "WHERE s.user_id = #{userId}")
    void deleteGuessesOnMySubmissions(@Param("userId") Long userId);

    @Delete("DELETE cm FROM comment_mentions cm JOIN comments c ON c.id = cm.comment_id WHERE c.user_id = #{userId}")
    void deleteMentionsOnMyComments(@Param("userId") Long userId);

    @Delete("DELETE FROM comment_mentions WHERE mentioned_user_id = #{userId}")
    void deleteMentionsOfMe(@Param("userId") Long userId);

    @Delete("DELETE FROM comments WHERE user_id = #{userId}")
    void deleteMyComments(@Param("userId") Long userId);

    @Delete("DELETE FROM reactions WHERE user_id = #{userId}")
    void deleteMyReactions(@Param("userId") Long userId);

    @Delete("DELETE FROM submission_guesses WHERE user_id = #{userId}")
    void deleteMyGuesses(@Param("userId") Long userId);

    @Delete("DELETE FROM submissions WHERE user_id = #{userId}")
    void deleteMySubmissions(@Param("userId") Long userId);

    @Delete("DELETE FROM pokes WHERE from_user_id = #{userId} OR to_user_id = #{userId}")
    void deleteMyPokes(@Param("userId") Long userId);

    @Delete("DELETE FROM notifications WHERE from_user_id = #{userId} OR to_user_id = #{userId}")
    void deleteMyNotifications(@Param("userId") Long userId);

    @Delete("DELETE FROM users WHERE id = #{userId}")
    int deleteUserById(@Param("userId") Long userId);
}

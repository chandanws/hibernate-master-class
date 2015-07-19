package com.vladmihalcea.book.high_performance_java_persistence.jdbc.batch;

import com.vladmihalcea.hibernate.masterclass.laboratory.util.DataSourceProviderIntegrationTest;
import org.junit.Test;

import javax.persistence.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

/**
 * AbstractBatchPreparedStatementTest - Base class for testing JDBC PreparedStatement  batching
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractBatchPreparedStatementTest extends DataSourceProviderIntegrationTest {

    public static final String INSERT_POST = "insert into Post (title, version, id) values (?, ?, ?)";

    public static final String INSERT_POST_COMMENT = "insert into PostComment (post_id, review, version, id) values (?, ?, ?, ?)";

    public AbstractBatchPreparedStatementTest(DataSourceProvider dataSourceProvider) {
        super(dataSourceProvider);
    }

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Post.class,
                PostDetails.class,
                Comment.class
        };
    }

    @Test
    public void testInsert() {
        LOGGER.info("Test batch insert");
        long startNanos = System.nanoTime();
        doInConnection(connection -> {
            try (PreparedStatement postStatement = connection.prepareStatement(INSERT_POST);
                 PreparedStatement postCommentStatement = connection.prepareStatement(INSERT_POST_COMMENT)) {
                int postCount = getPostCount();
                int postCommentCount = getPostCommentCount();

                for(int i = 0; i < postCount; i++) {
                    int index = 0;
                    postStatement.setString(++index, String.format("Post no. %1$d", i));
                    postStatement.setInt(++index, 0);
                    postStatement.setLong(++index, i);
                    onStatement(postStatement);
                    for(int j = 0; j < postCommentCount; j++) {
                        index = 0;

                        postCommentStatement.setLong(++index, i);
                        postCommentStatement.setString(++index, String.format("Post comment %1$d", j));
                        postCommentStatement.setInt(++index, 0);
                        postCommentStatement.setLong(++index, (postCommentCount * i) + j);
                        onStatement(postCommentStatement);
                        if((i + 1) * j % getBatchSize() == 0) {
                            onFlush(postStatement);
                            onFlush(postCommentStatement);
                        }
                    }
                }
                onEnd(postStatement);
                onEnd(postCommentStatement);
            } catch (SQLException e) {
                fail(e.getMessage());
            }
        });
        LOGGER.info("{}.testInsert for {} took {} millis",
                getClass().getSimpleName(),
                getDataSourceProvider().getClass().getSimpleName(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    protected abstract void onFlush(PreparedStatement statement) throws SQLException;

    protected abstract void onStatement(PreparedStatement statement) throws SQLException;

    protected abstract void onEnd(PreparedStatement statement) throws SQLException;

    protected int getPostCount() {
        return 1000;
    }

    protected int getPostCommentCount() {
        return 5;
    }

    protected int getBatchSize() {
        return 50;
    }

    @Entity(name = "Post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Version
        private int version;

        private Post() {
        }

        public Post(String title) {
            this.title = title;
        }

        @OneToMany(cascade = CascadeType.ALL, mappedBy = "post",
                orphanRemoval = true)
        private List<Comment> comments = new ArrayList<>();

        @OneToOne(cascade = CascadeType.ALL, mappedBy = "post",
                orphanRemoval = true, fetch = FetchType.LAZY)
        private PostDetails details;

        public void setTitle(String title) {
            this.title = title;
        }

        public List<Comment> getComments() {
            return comments;
        }

        public PostDetails getDetails() {
            return details;
        }

        public void addComment(Comment comment) {
            comments.add(comment);
            comment.setPost(this);
        }

        public void addDetails(PostDetails details) {
            this.details = details;
            details.setPost(this);
        }

        public void removeDetails() {
            this.details.setPost(null);
            this.details = null;
        }
    }

    @Entity(name = "PostDetails")
    public static class PostDetails {

        @Id
        private Long id;

        private Date createdOn;

        public PostDetails() {
            createdOn = new Date();
        }

        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "id")
        @MapsId
        private Post post;

        public Long getId() {
            return id;
        }

        public void setPost(Post post) {
            this.post = post;
        }
    }

    @Entity(name = "PostComment")
    public static class Comment {

        @Id
        private Long id;

        @ManyToOne
        private Post post;

        @Version
        private int version;

        private Comment() {
        }

        public Comment(String review) {
            this.review = review;
        }

        private String review;

        public Long getId() {
            return id;
        }

        public void setPost(Post post) {
            this.post = post;
        }
    }
}

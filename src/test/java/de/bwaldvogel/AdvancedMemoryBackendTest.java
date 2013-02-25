package de.bwaldvogel;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

import de.bwaldvogel.mongo.backend.Constants;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import de.bwaldvogel.mongo.wire.message.MongoServer;

public class AdvancedMemoryBackendTest {

    private Mongo client;
    private MongoServer mongoServer;
    private DBCollection collection;
    private DB db;

    @Before
    public void setUp() throws Exception {
        mongoServer = new MongoServer(new MemoryBackend());
        InetSocketAddress serverAddress = mongoServer.bind();
        client = new MongoClient(new ServerAddress(serverAddress));
        db = client.getDB("db");
        collection = db.getCollection("coll");
    }

    @After
    public void tearDown() {
        client.close();
        mongoServer.shutdownNow();
    }

    @Test
    public void testGetDb() {
        collection.insert(new BasicDBObject());
        assertThat(collection.getDB()).isNotNull();
        assertThat(collection.getDB()).isSameAs(client.getDB(db.getName()));
        assertThat(client.getDatabaseNames()).containsExactly(db.getName());
    }

    @Test
    public void testGetCollection() {
        DBCollection collection = db.getCollection("coll");
        db.getCollection("coll").insert(new BasicDBObject());

        assertThat(collection).isNotNull();
        assertThat(db.getCollection("coll")).isSameAs(collection);
        assertThat(db.getCollectionFromString("coll")).isSameAs(collection);
        assertThat(db.getCollectionNames()).containsOnly("coll");
    }

    @Test
    public void testCountCommand() {
        assertThat(collection.count()).isZero();
    }

    @Test
    public void testCountWithQueryCommand() {
        collection.insert(new BasicDBObject("n", 1));
        collection.insert(new BasicDBObject("n", 2));
        collection.insert(new BasicDBObject("n", 2));
        assertThat(collection.count(new BasicDBObject("n", 2))).isEqualTo(2);
    }

    @Test
    public void testInsertIncrementsCount() {
        assertThat(collection.count()).isZero();
        collection.insert(new BasicDBObject("key", "value"));
        assertThat(collection.count()).isEqualTo(1);
    }

    @Test
    public void testDeleteDecrementsCount() {
        collection.insert(new BasicDBObject("key", "value"));
        assertThat(collection.count()).isEqualTo(1);
        collection.remove(new BasicDBObject());
        assertThat(collection.count()).isZero();
    }

    @Test
    public void testFindOne() {
        collection.insert(new BasicDBObject("key", "value"));
        DBObject result = collection.findOne();
        assertThat(result).isNotNull();
        assertThat(result.get("_id")).isNotNull();
    }

    @Test
    public void testFindOneIn() {
        collection.insert(new BasicDBObject("_id", 1));
        DBObject result = collection.findOne(new BasicDBObject("_id", new BasicDBObject("$in", Arrays.asList(1, 2))));
        assertThat(result).isEqualTo(new BasicDBObject("_id", 1));
    }

    @Test
    public void testFindOneById() {
        collection.insert(new BasicDBObject("_id", 1));
        DBObject result = collection.findOne(new BasicDBObject("_id", 1));
        assertThat(result).isEqualTo(new BasicDBObject("_id", 1));
        assertThat(collection.findOne(new BasicDBObject("_id", 2))).isNull();
    }

    @Test
    public void testFindWithQuery() {
        collection.insert(new BasicDBObject("name", "jon"));
        collection.insert(new BasicDBObject("name", "leo"));
        collection.insert(new BasicDBObject("name", "neil"));
        collection.insert(new BasicDBObject("name", "neil"));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find(new BasicDBObject("name", "neil"));
        assertThat(cursor.toArray()).hasSize(2);
    }

    @Test
    public void testFindWithLimit() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 4));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find().limit(2);
        assertThat(cursor.toArray()).containsExactly(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2));
    }

    @Test
    public void testFindWithSkipLimit() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 4));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find().limit(2).skip(2);
        assertThat(cursor.toArray()).containsExactly(new BasicDBObject("_id", 3), new BasicDBObject("_id", 4));
    }

    @Test
    public void testFindWithSkipLimitAfterDelete() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 4));
        collection.insert(new BasicDBObject("_id", 5));

        collection.remove(new BasicDBObject("_id", 1));
        collection.remove(new BasicDBObject("_id", 3));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find().limit(2).skip(2);
        assertThat(cursor.toArray()).containsExactly(new BasicDBObject("_id", 5));
    }

    @Test
    public void testIdInQueryResultsInIndexOrder() {
        collection.insert(new BasicDBObject("_id", 4));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find(new BasicDBObject("_id", new BasicDBObject("$in", Arrays.asList(3, 2, 1))));
        assertThat(cursor.toArray()).containsExactly(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2),
                new BasicDBObject("_id", 3));
    }

    @Test
    public void testSort() {
        collection.insert(new BasicDBObject("a", 1).append("_id", 1));
        collection.insert(new BasicDBObject("a", 2).append("_id", 2));
        collection.insert(new BasicDBObject("_id", 5));
        collection.insert(new BasicDBObject("a", 3).append("_id", 3));
        collection.insert(new BasicDBObject("a", 4).append("_id", 4));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find().sort(new BasicDBObject("a", -1));
        assertThat(cursor.toArray()).containsExactly(new BasicDBObject("a", 4).append("_id", 4),
                new BasicDBObject("a", 3).append("_id", 3), new BasicDBObject("a", 2).append("_id", 2),
                new BasicDBObject("a", 1).append("_id", 1), new BasicDBObject("_id", 5));
    }

    @Test
    @Ignore("not yet implemented")
    public void testCompoundSort() {
        collection.insert(new BasicDBObject("a", 1).append("_id", 1));
        collection.insert(new BasicDBObject("a", 2).append("_id", 5));
        collection.insert(new BasicDBObject("a", 1).append("_id", 2));
        collection.insert(new BasicDBObject("a", 2).append("_id", 4));
        collection.insert(new BasicDBObject("a", 1).append("_id", 3));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find().sort(new BasicDBObject("a", 1).append("_id", -1));
        assertThat(cursor.toArray()).containsExactly(new BasicDBObject("a", 1).append("_id", 3),
                new BasicDBObject("a", 1).append("_id", 2), new BasicDBObject("a", 1).append("_id", 1),
                new BasicDBObject("a", 2).append("_id", 5), new BasicDBObject("a", 2).append("_id", 4));
    }

    @Test
    @Ignore("not yet implemented")
    public void testEmbeddedSort() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 4).append("counts", new BasicDBObject("done", 1)));
        collection.insert(new BasicDBObject("_id", 5).append("counts", new BasicDBObject("done", 2)));

        @SuppressWarnings("resource")
        DBCursor cursor = collection.find(new BasicDBObject("c", new BasicDBObject("$ne", true))).sort(
                new BasicDBObject("counts.done", -1));
        assertThat(cursor.toArray()).containsExactly(
                new BasicDBObject("_id", 5).append("counts", new BasicDBObject("done", 2)),
                new BasicDBObject("_id", 4).append("counts", new BasicDBObject("done", 1)),
                new BasicDBObject("_id", 1), new BasicDBObject("_id", 2), new BasicDBObject("_id", 3));
    }

    @Test
    public void testBasicUpdate() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2).append("b", 5));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 4));

        collection.update(new BasicDBObject("_id", 2), new BasicDBObject("a", 5));

        assertThat(collection.findOne(new BasicDBObject("_id", 2))).isEqualTo(
                new BasicDBObject("_id", 2).append("a", 5));
    }

    @Test
    public void testFullUpdateWithSameId() throws Exception {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2).append("b", 5));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 4));

        collection.update(new BasicDBObject("_id", 2).append("b", 5), new BasicDBObject("_id", 2).append("a", 5));

        assertThat(collection.findOne(new BasicDBObject("_id", 2))).isEqualTo(
                new BasicDBObject("_id", 2).append("a", 5));
    }

    @Test
    public void testUpdateIdNoChange() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.update(new BasicDBObject("_id", 1), new BasicDBObject("_id", 1).append("a", 5));

        assertThat(collection.findOne(new BasicDBObject("_id", 1))).isEqualTo(
                new BasicDBObject("_id", 1).append("a", 5));

        collection.update(new BasicDBObject("_id", 1),
                new BasicDBObject("$set", new BasicDBObject("_id", 1).append("b", 3)));

        assertThat(collection.findOne(new BasicDBObject("_id", 1))).isEqualTo(
                new BasicDBObject("_id", 1).append("a", 5).append("b", 3));

        // test with $set

        collection.update(new BasicDBObject("_id", 1),
                new BasicDBObject("$set", new BasicDBObject("_id", 1).append("a", 7)));

        assertThat(collection.findOne(new BasicDBObject("_id", 1))).isEqualTo(
                new BasicDBObject("_id", 1).append("a", 7).append("b", 3));
    }

    @Test
    public void testIdNotAllowedToBeUpdated() {
        collection.insert(new BasicDBObject("_id", 1));

        try {
            collection.update(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2).append("a", 5));
            fail("should throw exception");
        } catch (MongoException e) {
            assertThat(e.getMessage()).isEqualTo(
                    "cannot change _id of a document old:{ \"_id\" : 1} new:{ \"_id\" : 2}");
        }

        // test with $set

        try {
            collection.update(new BasicDBObject("_id", 1), new BasicDBObject("$set", new BasicDBObject("_id", 2)));
            fail("should throw exception");
        } catch (MongoException e) {
            assertThat(e.getMessage()).isEqualTo("Mod on _id not allowed");
        }
    }

    @Test
    public void testUpsert() {
        collection.update(new BasicDBObject("_id", 1).append("n", "jon"), new BasicDBObject("$inc", new BasicDBObject(
                "a", 1)), true, false);
        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", 1).append("n", "jon").append("a", 1));
    }

    @Test
    public void testUpsertWithConditional() {
        DBObject query = new BasicDBObject("_id", 1).append("b", new BasicDBObject("$gt", 5));
        BasicDBObject update = new BasicDBObject("$inc", new BasicDBObject("a", 1));
        collection.update(query, update, true, false);
        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", 1).append("a", 1));
    }

    @Test
    public void testUpsertWithIdIn() throws Exception {
        // {_id: {$in: [1]}}
        DBObject query = new BasicDBObjectBuilder().push("_id").append("$in", Arrays.asList(1)).pop().get();

        // {$push: {n: {_id: 2 ,u : 3}}, $inc: {c: 4}}
        DBObject update = new BasicDBObjectBuilder().push("$push").push("n") //
                .append("_id", 2).append("u", 3).pop().pop() //
                .push("$inc").append("c", 4).pop().get();

        DBObject expected = new BasicDBObjectBuilder().append("_id", 1)
                .append("n", Arrays.asList(new BasicDBObject("_id", 2) //
                        .append("u", 3))).append("c", 4).get();

        collection.update(query, update, true, false);

        // the ID generation actually differs from official MongoDB which just
        // create a random object id
        DBObject actual = collection.findOne();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testUpdatePush() throws Exception {
        BasicDBObject idObj = new BasicDBObject("_id", 1);
        collection.insert(idObj);
        collection.update(idObj, new BasicDBObject("$push", new BasicDBObject("field", "value")));
        DBObject expected = new BasicDBObject("_id", 1).append("field", Arrays.asList("value"));
        assertThat(collection.findOne(idObj)).isEqualTo(expected);

        // push to non-array
        collection.update(idObj, new BasicDBObject("$set", new BasicDBObject("field", "value")));
        try {
            collection.update(idObj, new BasicDBObject("$push", new BasicDBObject("field", "value")));
            fail("MongoException expected");
        } catch (MongoException e) {
            assertThat(e.getCode()).isEqualTo(10141);
            assertThat(e.getMessage()).isEqualTo("Cannot apply $push/$pushAll modifier to non-array");
        }

        // push with multiple fields

        DBObject pushObj = new BasicDBObject("$push", new BasicDBObject("field1", "value").append("field2", "value2"));
        collection.update(idObj, pushObj);

        expected = new BasicDBObject("_id", 1).append("field", "value") //
                .append("field1", Arrays.asList("value")) //
                .append("field2", Arrays.asList("value2"));
        assertThat(collection.findOne(idObj)).isEqualTo(expected);

        // push duplicate
        pushObj = new BasicDBObject("$push", new BasicDBObject("field1", "value"));
        collection.update(idObj, pushObj);
        expected.put("field1", Arrays.asList("value", "value"));
        assertThat(collection.findOne(idObj)).isEqualTo(expected);
    }

    @Test
    public void testUpdatePushAll() throws Exception {
        DBObject idObj = new BasicDBObject("_id", 1);
        collection.insert(idObj);
        try {
            collection.update(idObj, new BasicDBObject("$pushAll", new BasicDBObject("field", "value")));
            fail("MongoException expected");
        } catch (MongoException e) {
            assertThat(e.getCode()).isEqualTo(10153);
            assertThat(e.getMessage()).isEqualTo("Modifier $pushAll/pullAll allowed for arrays only");
        }

        collection.update(idObj, new BasicDBObject("$pushAll", //
                new BasicDBObject("field", Arrays.asList("value", "value2"))));
        DBObject expected = new BasicDBObject("_id", 1).append("field", Arrays.asList("value", "value2"));
        assertThat(collection.findOne(idObj)).isEqualTo(expected);
    }

    @Test
    public void testUpdateUnset() throws Exception {
        DBObject obj = new BasicDBObject("_id", 1).append("a", 1).append("b", null).append("c", "value");
        collection.insert(obj);
        try {
            collection.update(obj, new BasicDBObject("$unset", new BasicDBObject("_id", "")));
            fail("MongoException expected");
        } catch (MongoException e) {
            assertThat(e.getCode()).isEqualTo(10148);
            assertThat(e.getMessage()).isEqualTo("Mod on _id not allowed");
        }
        collection.update(obj, new BasicDBObject("$unset", new BasicDBObject("a", "").append("b", "")));
        DBObject expected = new BasicDBObject("_id", 1).append("c", "value");
        assertThat(collection.findOne()).isEqualTo(expected);
    }

    @Test
    public void testUpdateWithIdIn() {
        collection.insert(new BasicDBObject("_id", 1));
        DBObject query = new BasicDBObjectBuilder().push("_id").append("$in", Arrays.asList(1)).pop().get();
        DBObject update = new BasicDBObjectBuilder().push("$push").push("n").append("_id", 2).append("u", 3).pop()
                .pop().push("$inc").append("c", 4).pop().get();
        DBObject expected = new BasicDBObjectBuilder().append("_id", 1)
                .append("n", Arrays.asList(new BasicDBObject("_id", 2).append("u", 3))).append("c", 4).get();
        collection.update(query, update, false, true);
        assertThat(collection.findOne()).isEqualTo(expected);
    }

    @Test
    public void testUpdateWithObjectId() {
        collection.insert(new BasicDBObject("_id", new BasicDBObject("n", 1)));
        DBObject query = new BasicDBObject("_id", new BasicDBObject("n", 1));
        DBObject update = new BasicDBObject("$set", new BasicDBObject("a", 1));
        collection.update(query, update, false, false);
        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", new BasicDBObject("n", 1)).append("a", 1));
    }

    @Test
    public void testUpdateWithIdInMulti() {
        collection.insert(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2));
        collection.update(new BasicDBObject("_id", new BasicDBObject("$in", Arrays.asList(1, 2))), new BasicDBObject(
                "$set", new BasicDBObject("n", 1)), false, true);
        List<DBObject> results = collection.find().toArray();
        assertThat(results).containsExactly(new BasicDBObject("_id", 1).append("n", 1),
                new BasicDBObject("_id", 2).append("n", 1));
    }

    @Test
    public void testUpdateWithIdQuery() {
        collection.insert(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2));
        collection.update(new BasicDBObject("_id", new BasicDBObject("$gt", 1)), new BasicDBObject("$set",
                new BasicDBObject("n", 1)), false, true);
        List<DBObject> results = collection.find().toArray();
        assertThat(results).containsExactly(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2).append("n", 1));
    }

    @Test
    @Ignore("not yet implemented")
    public void testCompoundDateIdUpserts() {
        DBObject query = new BasicDBObjectBuilder().push("_id").push("$lt").add("n", "a").add("t", 10).pop()
                .push("$gte").add("n", "a").add("t", 1).pop().pop().get();
        List<BasicDBObject> toUpsert = Arrays.asList(
                new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 1)), new BasicDBObject("_id",
                        new BasicDBObject("n", "a").append("t", 2)), new BasicDBObject("_id", new BasicDBObject("n",
                        "a").append("t", 3)), new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 11)));
        for (BasicDBObject dbo : toUpsert) {
            collection.update(dbo, ((BasicDBObject) dbo.copy()).append("foo", "bar"), true, false);
        }
        List<DBObject> results = collection.find(query).toArray();
        assertThat(results).containsExactly(
                new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 1)).append("foo", "bar"),
                new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 2)).append("foo", "bar"),
                new BasicDBObject("_id", new BasicDBObject("n", "a").append("t", 3)).append("foo", "bar"));
    }

    @Test
    @Ignore("not yet implemented (illegal query key)")
    public void testAnotherUpsert() {
        BasicDBObjectBuilder queryBuilder = BasicDBObjectBuilder.start().push("_id").append("f", "ca").push("1")
                .append("l", 2).pop().push("t").append("t", 11).pop().pop();
        DBObject query = queryBuilder.get();

        DBObject update = BasicDBObjectBuilder.start().push("$inc").append("n.!", 1).append("n.a.b:false", 1).pop()
                .get();
        collection.update(query, update, true, false);

        DBObject expected = queryBuilder.push("n").append("!", 1).push("a").append("b:false", 1).pop().pop().get();
        assertThat(collection.findOne()).isEqualTo(expected);
    }

    @Test
    public void testUpsertOnIdWithPush() {
        DBObject update1 = BasicDBObjectBuilder.start().push("$push").push("c").append("a", 1) //
                .append("b", 2).pop().pop().get();

        DBObject update2 = BasicDBObjectBuilder.start().push("$push").push("c").append("a", 3) //
                .append("b", 4).pop().pop().get();

        collection.update(new BasicDBObject("_id", 1), update1, true, false);

        collection.update(new BasicDBObject("_id", 1), update2, true, false);

        DBObject expected = new BasicDBObject("_id", 1).append("c",
                Arrays.asList(new BasicDBObject("a", 1).append("b", 2), //
                        new BasicDBObject("a", 3).append("b", 4)));

        assertThat(collection.findOne(new BasicDBObject("c.a", 3).append("c.b", 4))).isEqualTo(expected);
    }

    @Test
    public void testUpsertWithEmbeddedQuery() {
        DBObject update = BasicDBObjectBuilder.start().push("$set").append("a", 1).pop().get();

        collection.update(new BasicDBObject("_id", 1).append("e.i", 1), update, true, false);

        DBObject expected = BasicDBObjectBuilder.start().append("_id", 1).push("e") //
                .append("i", 1).pop().append("a", 1).get();

        assertThat(collection.findOne(new BasicDBObject("_id", 1))).isEqualTo(expected);
    }

    @Test
    public void testFindAndModifyNotFound() throws Exception {
        collection.insert(new BasicDBObject("_id", 1).append("a", 1));
        DBObject result = collection.findAndModify(new BasicDBObject("_id", 2), null, null, false, new BasicDBObject(
                "$inc", new BasicDBObject("a", 1)), false, false);

        assertThat(result).isNull();
        assertThat(collection.count()).isEqualTo(1);
    }

    @Test
    public void testFindAndModifyCommandEmpty() throws Exception {
        DBObject cmd = new BasicDBObject("findandmodify", collection.getName());
        CommandResult result = db.command(cmd);
        assertThat(result.getErrorMessage()).isEqualTo("need remove or update");
        assertThat(result.ok()).isFalse();
    }

    @Test
    public void testFindAndModifyCommandUpdate() throws Exception {
        collection.insert(new BasicDBObject("_id", 1));

        DBObject cmd = new BasicDBObject("findAndModify", collection.getName());
        cmd.put("query", new BasicDBObject("_id", 1));
        cmd.put("update", new BasicDBObject("$inc", new BasicDBObject("a", 1)));

        CommandResult result = db.command(cmd);
        assertThat(result.get("lastErrorObject")).isEqualTo(
                new BasicDBObject("updatedExisting", Boolean.TRUE).append("n", 1));

        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", 1).append("a", 1));
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void testFindAndModifyCommandIllegalOp() throws Exception {
        collection.insert(new BasicDBObject("_id", 1));

        DBObject cmd = new BasicDBObject("findAndModify", collection.getName());
        cmd.put("query", new BasicDBObject("_id", 1));
        cmd.put("update", new BasicDBObject("$inc", new BasicDBObject("_id", 1)));

        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", 1));

        CommandResult result = db.command(cmd);
        try {
            result.throwOnError();
            fail("MongoException expected");
        } catch (MongoException e) {
            assertThat(e.getCode()).isEqualTo(10148);
            assertThat(e.getMessage()).contains("Mod on _id not allowed");
        }
    }

    @Test
    public void testFindAndModifyError() throws Exception {

        collection.insert(new BasicDBObject("_id", 1).append("a", 1));

        try {
            collection.findAndModify(new BasicDBObject("_id", 1), null, null, false, new BasicDBObject("$inc",
                    new BasicDBObject("_id", 1)), false, false);
            fail("MongoException expected");
        } catch (MongoException e) {
            assertThat(e.getCode()).isEqualTo(10148);
            assertThat(e.getMessage()).contains("command failed [findandmodify]");
            assertThat(e.getMessage()).contains("Mod on _id not allowed");
        }
    }

    @Test
    public void testFindAndModifyReturnOld() {
        collection.insert(new BasicDBObject("_id", 1).append("a", 1));
        DBObject result = collection.findAndModify(new BasicDBObject("_id", 1), null, null, false, new BasicDBObject(
                "$inc", new BasicDBObject("a", 1)), false, false);

        assertThat(result).isEqualTo(new BasicDBObject("_id", 1).append("a", 1));
        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", 1).append("a", 2));
    }

    @Test
    public void testFindAndModifyReturnNew() {
        collection.insert(new BasicDBObject("_id", 1).append("a", 1));
        DBObject result = collection.findAndModify(new BasicDBObject("_id", 1), null, null, false, new BasicDBObject(
                "$inc", new BasicDBObject("a", 1)), true, false);

        assertThat(result).isEqualTo(new BasicDBObject("_id", 1).append("a", 2));
    }

    @Test
    public void testFindAndModifyFields() throws Exception {
        client = new MongoClient("localhost");
        db = client.getDB("test");
        collection = db.getCollection("test");
        collection.drop();
        collection.insert(new BasicDBObject("_id", 1).append("a", 1));
        DBObject result = collection.findAndModify(new BasicDBObject("_id", 1), new BasicDBObject("_id", 1), null,
                false, new BasicDBObject("$inc", new BasicDBObject("a", 1)), true, false);

        assertThat(result).isEqualTo(new BasicDBObject("_id", 1));
    }

    @Test
    public void testFindAndModifySorted() throws Exception {
        collection.insert(new BasicDBObject("_id", 1).append("a", 15));
        collection.insert(new BasicDBObject("_id", 2).append("a", 10));
        collection.insert(new BasicDBObject("_id", 3).append("a", 20));

        DBObject order = new BasicDBObject("a", 1);
        DBObject result = collection.findAndModify(new BasicDBObject(), null, order, false, new BasicDBObject("$inc",
                new BasicDBObject("a", 1)), true, false);
        assertThat(result).isEqualTo(new BasicDBObject("_id", 2).append("a", 11));

        order = new BasicDBObject("a", -1);
        result = collection.findAndModify(new BasicDBObject(), null, order, false, new BasicDBObject("$inc",
                new BasicDBObject("a", 1)), true, false);
        assertThat(result).isEqualTo(new BasicDBObject("_id", 3).append("a", 21));

    }

    @Test
    public void testFindAndModifyUpsert() {
        DBObject result = collection.findAndModify(new BasicDBObject("_id", 1), null, null, false, new BasicDBObject(
                "$inc", new BasicDBObject("a", 1)), true, true);

        assertThat(result).isEqualTo(new BasicDBObject("_id", 1).append("a", 1));
        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", 1).append("a", 1));
    }

    @Test
    public void testFindAndModifyUpsertReturnNewFalse() {
        DBObject result = collection.findAndModify(new BasicDBObject("_id", 1), null, null, false, new BasicDBObject(
                "$inc", new BasicDBObject("a", 1)), false, true);

        assertThat(result).isEqualTo(new BasicDBObject());
        assertThat(collection.findOne()).isEqualTo(new BasicDBObject("_id", 1).append("a", 1));
    }

    @Test
    public void testFindAndRemoveFromEmbeddedList() {
        BasicDBObject obj = new BasicDBObject("_id", 1).append("a", Arrays.asList(1));
        collection.insert(obj);
        DBObject result = collection.findAndRemove(new BasicDBObject("_id", 1));
        assertThat(result).isEqualTo(obj);
        assertThat(collection.count()).isZero();
    }

    @Test
    public void testFindAndModifyRemove() {
        collection.insert(new BasicDBObject("_id", 1).append("a", 1));
        DBObject result = collection.findAndModify(new BasicDBObject("_id", 1), null, null, true, null, false, false);

        assertThat(result).isEqualTo(new BasicDBObject("_id", 1).append("a", 1));
        assertThat(collection.count()).isZero();
    }

    @Test
    public void testRemove() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 2));
        collection.insert(new BasicDBObject("_id", 3));
        collection.insert(new BasicDBObject("_id", 4));

        collection.remove(new BasicDBObject("_id", 2));

        assertThat(collection.findOne(new BasicDBObject("_id", 2))).isNull();
    }

    @Test
    @Ignore("not yet implemented")
    public void testDistinctQuery() {
        collection.insert(new BasicDBObject("n", 1).append("_id", 1));
        collection.insert(new BasicDBObject("n", 2).append("_id", 2));
        collection.insert(new BasicDBObject("n", 3).append("_id", 3));
        collection.insert(new BasicDBObject("n", 1).append("_id", 4));
        collection.insert(new BasicDBObject("n", 1).append("_id", 5));
        assertThat(collection.distinct("n")).containsExactly(1, 2, 3);
    }

    @Test
    public void testGetLastError() {
        collection.insert(new BasicDBObject("_id", 1));
        CommandResult error = db.getLastError();
        assertThat(error.ok()).isTrue();

        assertThat(db.command("illegalCommand").ok()).isFalse();

        // getlasterror must succeed again
        assertThat(db.getLastError().ok()).isTrue();
    }

    @Test
    public void testSave() {
        BasicDBObject inserted = new BasicDBObject("_id", 1);
        collection.insert(inserted);
        collection.save(inserted);
    }

    @Test(expected = MongoException.DuplicateKey.class)
    public void testInsertDuplicateWithConcernThrows() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 1), WriteConcern.SAFE);
    }

    @Test(expected = MongoException.DuplicateKey.class)
    public void testInsertDuplicateThrows() {
        collection.insert(new BasicDBObject("_id", 1));
        collection.insert(new BasicDBObject("_id", 1));
    }

    @Test
    @Ignore("not yet implemented")
    public void testCreateIndexes() {
        collection.ensureIndex("n");
        collection.ensureIndex("b");
        List<DBObject> indexes = db.getCollection("system.indexes").find().toArray();
        assertThat(indexes).containsExactly(
                new BasicDBObject("v", 1).append("key", new BasicDBObject("n", 1)).append("ns", "db.coll")
                        .append("name", "n_1"),
                new BasicDBObject("v", 1).append("key", new BasicDBObject("b", 1)).append("ns", "db.coll")
                        .append("name", "b_1"));
    }

    @Test
    @Ignore("not yet implemented")
    public void testSortByEmbeddedKey() {
        collection.insert(new BasicDBObject("_id", 1).append("a", new BasicDBObject("b", 1)));
        collection.insert(new BasicDBObject("_id", 2).append("a", new BasicDBObject("b", 2)));
        collection.insert(new BasicDBObject("_id", 3).append("a", new BasicDBObject("b", 3)));
        List<DBObject> results = collection.find().sort(new BasicDBObject("a.b", -1)).toArray();
        assertThat(results).containsExactly(new BasicDBObject("_id", 3).append("a", new BasicDBObject("b", 3)),
                new BasicDBObject("_id", 2).append("a", new BasicDBObject("b", 2)),
                new BasicDBObject("_id", 1).append("a", new BasicDBObject("b", 1)));
    }

    @Test
    public void testInsertReturnsModifiedDocumentCount() {
        WriteResult result = collection.insert(new BasicDBObject("_id", new BasicDBObject("n", 1)));
        assertThat(result.getN()).isEqualTo(1);
    }

    @Test
    public void testRemoveReturnsModifiedDocumentCount() {
        collection.insert(new BasicDBObject());
        collection.insert(new BasicDBObject());

        WriteResult result = collection.remove(new BasicDBObject());
        assertThat(result.getN()).isEqualTo(2);
    }

    @Test
    public void testUpdateWithIdInMultiReturnModifiedDocumentCount() {
        collection.insert(new BasicDBObject("_id", 1), new BasicDBObject("_id", 2));
        BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$in", Arrays.asList(1, 2)));
        BasicDBObject update = new BasicDBObject("$set", new BasicDBObject("n", 1));
        WriteResult result = collection.update(query, update, false, true);
        assertThat(result.getN()).isEqualTo(2);
    }

    @Test
    public void testUpdateWithObjectIdReturnModifiedDocumentCount() {
        collection.insert(new BasicDBObject("_id", new BasicDBObject("n", 1)));
        DBObject query = new BasicDBObject("_id", new BasicDBObject("n", 1));
        DBObject update = new BasicDBObject("$set", new BasicDBObject("a", 1));
        WriteResult result = collection.update(query, update, false, false);
        assertThat(result.getN()).isEqualTo(1);
    }

    /**
     * Test that ObjectId is getting generated even if _id is present in
     * DBObject but it's value is null
     *
     * @throws Exception
     */
    @Test
    public void testIdGenerated() throws Exception {
        DBObject toSave = new BasicDBObject();
        toSave.put("_id", null);
        toSave.put("name", "test");

        collection.save(toSave);
        DBObject result = collection.findOne(new BasicDBObject("name", "test"));
        assertThat(result.get(Constants.ID_FIELD)).isInstanceOf(ObjectId.class);
    }

    @Test
    public void testDropDatabaseAlsoDropsCollectionData() throws Exception {
        collection.insert(new BasicDBObject());
        db.dropDatabase();
        assertThat(collection.count()).isZero();
    }

    @Test
    public void testDropCollectionAlsoDropsFromDB() throws Exception {
        collection.insert(new BasicDBObject());
        collection.drop();
        assertThat(collection.count()).isZero();
        assertThat(db.getCollectionNames()).excludes(collection.getName());
    }

    @Test
    public void testDropDatabaseDropsAllData() throws Exception {
        collection.insert(new BasicDBObject());
        client.dropDatabase(db.getName());
        assertThat(client.getDatabaseNames()).excludes(db.getName());
        assertThat(collection.count()).isZero();
        assertThat(db.getCollectionNames()).excludes(collection.getName());
    }

}

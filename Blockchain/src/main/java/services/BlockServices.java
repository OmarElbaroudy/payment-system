package services;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.commons.codec.digest.DigestUtils;
import persistence.MongoHandler;
import persistence.RocksHandler;
import persistence.models.Block;
import persistence.models.MetaData;
import persistence.models.Transaction;
import persistence.models.UTXO;
import utilities.MerkelTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BlockServices {

    public static Block mineBlock(List<Transaction> transactions, MongoHandler handler) {
        Block lst = getLastBlock(handler);
        String prevHash = hash(Objects.requireNonNull(lst).toString());
        String path = "/home/baroudy/Projects/Bachelor/payment-system";
        Dotenv dotenv = Dotenv.configure().directory(path).load();

        int nonce = 0;
        int idx = lst.getIdx();
        int difficulty = Integer.parseInt(
                Objects.requireNonNull(dotenv.get("DIFFICULTY")));

        for (boolean flag = false; !flag; nonce++) {
            MetaData data = new MetaData(idx, prevHash, nonce, difficulty);
            MerkelTree tree = new MerkelTree(transactions);
            Block b = new Block(data, tree);
            String hashedValue = hash(b.toString());
            flag = check(hashedValue, difficulty);
        }

        MetaData data = new MetaData(idx, prevHash, nonce, difficulty);
        MerkelTree tree = new MerkelTree(transactions);
        return new Block(data, tree);
    }

    private static boolean check(String s, int diff) {
        for (int i = 0; i < diff; i++) {
            if (s.charAt(i) != '0') return false;
        }
        return true;
    }

    private static String hash(String block) {
        return new DigestUtils("SHA3-256").digestAsHex(block);
    }

    private static Block getLastBlock(MongoHandler handler) {
        int idx = 1;
        Block lst = handler.getBlock(1), b = lst;

        while (b != null) {
            lst = b;
            b = handler.getBlock(++idx);
        }

        return lst;
    }

    /**
     * @param block      that is needed to be validated and added
     * @param difficulty at which the target hash is checked
     * @param handler    for inserting the block in the db
     * @return list of transactions if block is validated and added successfully
     * or null if block is invalid. if a block already exists in the database
     * with the same index it will be replaced by the validated block
     */
    public static List<Transaction> validateAndAddBlock(Block block, int difficulty, MongoHandler handler) {
        Block lst = getLastBlock(handler);
        if (lst == null) return null; //Genesis

        boolean flag = lst.getIdx() == block.getIdx() - 1;
        String hashLast = hash(lst.toString());
        flag &= hashLast.equals(block.getMetaData().getPreviousBlockHash());
        flag &= difficulty == block.getMetaData().getDifficulty();

        String hashVal = hash(block.toString());
        for (int i = 0; i < difficulty; i++) {
            flag &= hashVal.charAt(i) == '0';
        }

        if (!flag) return null;

        handler.saveBlock(block);
        return block.getTransactions().getTransactions();
    }

    public static boolean blockExists(Block block, MongoHandler handler) {
        Block comp = handler.getBlock(block.getIdx());
        return comp != null && comp.equals(block);
    }

    public static void generateGenesis(MongoHandler handler, RocksHandler rocksHandler) {
        String path = "/home/baroudy/Projects/Bachelor/payment-system";
        Dotenv dotenv = Dotenv.configure().directory(path).load();

        String prevHash = dotenv.get("GENESIS_PREVIOUS_HASH");
        int nonce = Integer.parseInt(Objects.requireNonNull(dotenv.get("GENESIS_NONCE")));
        String pubKey = dotenv.get("GENESIS_PUBLIC_KEY");

        MetaData data = new MetaData(1, prevHash, nonce, 0);

        UTXO output = new UTXO(1e18, pubKey);
        Transaction transaction = new Transaction(new ArrayList<>(), output);

        Block b = new Block(data, new MerkelTree(List.of(transaction)));
        handler.saveBlock(b);
        rocksHandler.update(b);
    }
}

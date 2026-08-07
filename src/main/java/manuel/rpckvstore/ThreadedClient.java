package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;


public class ThreadedClient{
    static BaseServer stub;
    public String TauUniprot = "P10636";
    public String TauSequence = "MAEPRQEFEVMEDHAGTYGLGDRKDQGGYTMHQDQEGDTDAGLKESPLQTPTEDGSEEPGSETSDAKSTPTAEDVTAPLVDEGAPGKQAAAQPHTEIPEGTTAEEAGIGDTPSLEDEAAGHVTQEPESGKVVQEGFLREPGPPGLSHQLMSGMPGAPLLPEGPREATRQPSGTGPEDTEGGRHAPELLKHQLLGDLHQEGPPLKGAGGKERPGSKEEVDEDRDVDESSPQDSPPSKASPAQDGRPPQTAAREATSIPGFPAEGAIPLPVDFLSKVSTEIPASEPDGPSVGRAKGQDAPLEFTFHVEITPNVQKEQAHSEEHLGRAAFPGAPGEGPEARGPSLGEDTKEADLPEPSEKQPAAAPRGKPVSRVPQLKARMVSKSKDGTGSDDKKAKTSTRSSAKTLKNRPCLSPKHPTPGSSDPLIQPSSPAVCPEPPSSPKYVSSVTSRTGSSGAKEMKLKGADGKTKIATPRGAAPPGQKGQANATRIPAKTPPAPKTPPSSGEPPKSGDRSGYSSPGSPGTPGSRSRTPSLPTPPTREPKKVAVVRTPPKSPSSAKSRLQTAPVPMPDLKNVKSKIGSTENLKHQPGGGKVQIINKKLDLSNVQSKCGSKDNIKHVPGGGSVQIVYKPVDLSKVTSKCGSLGNIHHKPGGGQVEVKSEKLDFKDRVQSKIGSLDNITHVPGGGNKKIETHKLTFRENAKAKTDHGAEIVYKSPVVSGDTSPRHLSNVSSTGSIDMVDSPQLATLADEVSASLAKQGL";
    public String AmyloidBetaUniprot = "P05067";
    public String AmyloidBetaSquence = "MLPGLALLLLAAWTARALEVPTDGNAGLLAEPQIAMFCGRLNMHMNVQNGKWDSDPSGTKTCIDTKEGILQYCQEVYPELQITNVVEANQPVTIQNWCKRGRKQCKTHPHFVIPYRCLVGEFVSDALLVPDKCKFLHQERMDVCETHLHWHTVAKETCSEKSTNLHDYGMLLPCGIDKFRGVEFVCCPLAEESDNVDSADAEEDDSDVWWGGADTDYADGSEDKVVEVAEEEEVAEVEEEEADDDEDDEDGDEVEEEAEEPYEEATERTTSIATTTTTTTESVEEVVREVCSEQAETGPCRAMISRWYFDVTEGKCAPFFYGGCGGNRNNFDTEEYCMAVCGSAMSQSLLKTTQEPLARDPVKLPTTAASTPDAVDKYLETPGDENEHAHFQKAKERLEAKHRERMSQVMREWEEAERQAKNLPKADKKAVIQHFQEKVESLEQEAANERQQLVETHMARVEAMLNDRRRLALENYITALQAVPPRPRHVFNMLKKYVRAEQKDRQHTLKHFEHVRMVDPKKAAQIRSQVMTHLRVIYERMNQSLSLLYNVPAVAEEIQDEVDELLQKEQNYSDDVLANMISEPRISYGNDALMPSLTETKTTVELLPVNGEFSLDDLQPWHSFGADSVPANTENEVEPVDARPAADRGLTTRPGSGLTNIKTEEISEVKMDAEFRHDSGYEVHHQKLVFFAEDVGSNKGAIIGLMVGGVVIATVIVITLVMLKKKQYTSIHHGVVEVDAAVTPEERHLSKMQQNGYENPTYKFFEQMQN";
    public String TRIM11Uniport = "Q96F44";
    public String TRIM11Sequence = "MAAPDLSTNLQEEATCAICLDYFTDPVMTDCGHNFCRECIRRCWGQPEGPYACPECRELSPQRNLRPNRPLAKMAEMARRLHPPSPVPQGVCPAHREPLAAFCGDELRLLCAACERSGEHWAHRVRPLQDAAEDLKAKLEKSLEHLRKQMQDALLFQAQADETCVLWQKMVESQRQNVLGEFERLRRLLAEEEQQLLQRLEEEELEVLPRLREGAAHLGQQSAHLAELIAELEGRCQLPALGLLQDIKDALRRVQDVKLQPPEVVPMELRTVCRVPGLVETLRRFRGDVTLDPDTANPELILSEDRRSVQRGDLRQALPDSPERFDPGPCVLGQERFTSGRHYWEVEVGDRTSWALGVCRENVNRKEKGELSAGNGFWILVFLGSYYNSSERALAPLRDPPRRVGIFLDYEAGHLSFYSATDGSLLFIFPEIPFSGTLRPLFSPLSSSPTPMTICRPKGGSGDTLAPQ";
    public String Exephrin5Uniprot = "P52803";
    public String Exerphin5Sequence = "MLHVEMLTLVFLVLWMCVFSQDPGSKAVADRYAVYWNSSNPRFQRGDYHIDVCINDYLDVFCPHYEDSVPEDKTERYVLYMVNFDGYSACDHTSKGFKRWECNRPHSPNGPLKFSEKFQLFTPFSLGFEFRPGREYFYISSAIPDNGRRSCLKLKVFVRPTNSCMKTIGVHDRVFDVNDKVENSLEPADDTVHESAEPSRGENAAQTPRIPSRLLAILLFLLAMLLTL";
    public String APOEUniport = "P02649";
    public String APOESequence = "MKVLWAALLVTFLAGCQAKVEQAVETEPEPELRQQTEWQSGQRWELALGRFWDYLRWVQTLSEQVQEELLSSQVTQELRALMDETMKELKAYKSELEEQLTPVAEETRARLSKELQAAQARLGADMEDVCGRLVQYRGEVQAMLGQSTEELRVRLASHLRKLRKRLLRDADDLQKRLAVYQAGAREGAERGLSAIRERLGPLVEQGRVRAATVGSLAGQPLQERAQAWGERLRARMEEMGSRTRDRLDEVKEQVAEVRAKLEEQAQQIRLQAEAFQARLKSWFEPLVEDMQRQWAGLVEKVQAAVGTSAAPVPSDNH";
    public String BACE1Uniprot = "P56817";
    public String BACE1Sequence = "MAQALPWLLLWMGAGVLPAHGTQHGIRLPLRSGLGGAPLGLRLPRETDEEPEEPGRRGSFVEMVDNLRGKSGQGYYVEMTVGSPPQTLNILVDTGSSNFAVGAAPHPFLHRYYQRQLSSTYRDLRKGVYVPYTQGKWEGELGTDLVSIPHGPNVTVRANIAAITESDKFFINGSNWEGILGLAYAEIARPDDSLEPFFDSLVKQTHVPNLFSLQLCGAGFPLNQSEVLASVGGSMIIGGIDHSLYTGSLWYTPIRREWYYEVIIVRVEINGQDLKMDCKEYNYDKSIVDSGTTNLRLPKKVFEAAVKSIKAASSTEKFPDGFWLGEQLVCWQAGTTPWNIFPVISLYLMGEVTNQSFRITILPQQYLRPVEDVATSQDDCYKFAISQSSTGTVMGAVIMEGFYVVFDRARKRIGFAVSACHVHDEFRTAAVEGPFVTLDMEDCGYNIPQTDESTLMTIAYVMAAICALFMLPLCLMVCQWRCLRCLRQQHDDFADDISLLK";
    public HashMap<String, String> protiens;
    private Registry cRegistry;


    ThreadedClient(String IP, int port) throws RemoteException {
        if (protiens == null) {
            setProtein();
        }
        this.cRegistry = LocateRegistry.getRegistry(IP, port);
    }
    public Registry getRegistry() {
            return this.cRegistry;
        }

    public final BaseServer getStub() throws NotBoundException, RemoteException {
        System.out.println("Get stub");
        Registry r = this.getRegistry();
        return (BaseServer) r.lookup("Node-"+0);
    }



    public void setProtein() {
        protiens = new HashMap<>();
        protiens.put(TauUniprot, TauSequence);
        protiens.put(AmyloidBetaUniprot, AmyloidBetaSquence);
        protiens.put(TRIM11Uniport, TRIM11Sequence);
        protiens.put(Exephrin5Uniprot, Exerphin5Sequence);
        protiens.put(APOEUniport, APOESequence);
        protiens.put(BACE1Uniprot, BACE1Sequence);
    }

    private  HashMap<String, String> getProteins() {
        if (this.protiens == null) {
            setProtein();
        }
        return protiens;
    }

    public static void main(String[] args) throws RemoteException, NotBoundException {
        if (args.length < 2) {
            System.err.println("Port Number and IP Address Must be Provided");
            System.exit(1);
        }

        String IPString = args[0];
        String PortString = args[1];
        int PortNumber;

        try {
            PortNumber = Integer.parseInt(PortString);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + PortString);
            System.exit(1);
            return;
        }

        System.out.println("Attempting to connect to server at " + IPString + ":" + PortNumber);
        ThreadedClient c = new ThreadedClient(IPString, PortNumber);
        stub = c.getStub();
        System.out.println("Connected successfully!");

        // One thread per protein (each a distinct key) so the store is exercised
        // by genuinely concurrent clients -- the case per-key Paxos must handle.
        ArrayList<PutGetDeleteThread> tasks = new ArrayList<>();
        ArrayList<Thread> threads = new ArrayList<>();
        for (String key : c.getProteins().keySet()) {
            PutGetDeleteThread task = new PutGetDeleteThread(stub, key, c.getProteins().get(key));
            tasks.add(task);
            threads.add(new Thread(task, key));
        }
        for (Thread t : threads) {
            t.start();
            System.out.println(t.getName() + " | started");
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long passed = tasks.stream().filter(PutGetDeleteThread::isSuccess).count();
        System.out.println("=== ThreadedClient summary: " + passed + "/" + tasks.size()
                + " proteins round-tripped ===");
        if (passed != tasks.size()) {
            System.exit(1);
        }
    }

}

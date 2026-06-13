package manuel.rpckvstore;

import manuel.rpckvstore.Node.BaseServer;
import manuel.rpckvstore.Packet.Packet;

import java.rmi.RemoteException;

public class Example {


    // Tau
    String TauUniprot = "P10636";
    String TauSequence = "MAEPRQEFEVMEDHAGTYGLGDRKDQGGYTMHQDQEGDTDAGLKESPLQTPTEDGSEEPGSETSDAKSTPTAEDVTAPLVDEGAPGKQAAAQPHTEIPEGTTAEEAGIGDTPSLEDEAAGHVTQEPESGKVVQEGFLREPGPPGLSHQLMSGMPGAPLLPEGPREATRQPSGTGPEDTEGGRHAPELLKHQLLGDLHQEGPPLKGAGGKERPGSKEEVDEDRDVDESSPQDSPPSKASPAQDGRPPQTAAREATSIPGFPAEGAIPLPVDFLSKVSTEIPASEPDGPSVGRAKGQDAPLEFTFHVEITPNVQKEQAHSEEHLGRAAFPGAPGEGPEARGPSLGEDTKEADLPEPSEKQPAAAPRGKPVSRVPQLKARMVSKSKDGTGSDDKKAKTSTRSSAKTLKNRPCLSPKHPTPGSSDPLIQPSSPAVCPEPPSSPKYVSSVTSRTGSSGAKEMKLKGADGKTKIATPRGAAPPGQKGQANATRIPAKTPPAPKTPPSSGEPPKSGDRSGYSSPGSPGTPGSRSRTPSLPTPPTREPKKVAVVRTPPKSPSSAKSRLQTAPVPMPDLKNVKSKIGSTENLKHQPGGGKVQIINKKLDLSNVQSKCGSKDNIKHVPGGGSVQIVYKPVDLSKVTSKCGSLGNIHHKPGGGQVEVKSEKLDFKDRVQSKIGSLDNITHVPGGGNKKIETHKLTFRENAKAKTDHGAEIVYKSPVVSGDTSPRHLSNVSSTGSIDMVDSPQLATLADEVSASLAKQGL";
    // AmyloidBeta
    String AmyloidBetaUniprot = "P05067";
    String AmyloidBetaSquence = "MLPGLALLLLAAWTARALEVPTDGNAGLLAEPQIAMFCGRLNMHMNVQNGKWDSDPSGTKTCIDTKEGILQYCQEVYPELQITNVVEANQPVTIQNWCKRGRKQCKTHPHFVIPYRCLVGEFVSDALLVPDKCKFLHQERMDVCETHLHWHTVAKETCSEKSTNLHDYGMLLPCGIDKFRGVEFVCCPLAEESDNVDSADAEEDDSDVWWGGADTDYADGSEDKVVEVAEEEEVAEVEEEEADDDEDDEDGDEVEEEAEEPYEEATERTTSIATTTTTTTESVEEVVREVCSEQAETGPCRAMISRWYFDVTEGKCAPFFYGGCGGNRNNFDTEEYCMAVCGSAMSQSLLKTTQEPLARDPVKLPTTAASTPDAVDKYLETPGDENEHAHFQKAKERLEAKHRERMSQVMREWEEAERQAKNLPKADKKAVIQHFQEKVESLEQEAANERQQLVETHMARVEAMLNDRRRLALENYITALQAVPPRPRHVFNMLKKYVRAEQKDRQHTLKHFEHVRMVDPKKAAQIRSQVMTHLRVIYERMNQSLSLLYNVPAVAEEIQDEVDELLQKEQNYSDDVLANMISEPRISYGNDALMPSLTETKTTVELLPVNGEFSLDDLQPWHSFGADSVPANTENEVEPVDARPAADRGLTTRPGSGLTNIKTEEISEVKMDAEFRHDSGYEVHHQKLVFFAEDVGSNKGAIIGLMVGGVVIATVIVITLVMLKKKQYTSIHHGVVEVDAAVTPEERHLSKMQQNGYENPTYKFFEQMQN";
    //        TRIM11
    String TRIM11Uniport = "Q96F44";
    String TRIM11Sequence = "MAAPDLSTNLQEEATCAICLDYFTDPVMTDCGHNFCRECIRRCWGQPEGPYACPECRELSPQRNLRPNRPLAKMAEMARRLHPPSPVPQGVCPAHREPLAAFCGDELRLLCAACERSGEHWAHRVRPLQDAAEDLKAKLEKSLEHLRKQMQDALLFQAQADETCVLWQKMVESQRQNVLGEFERLRRLLAEEEQQLLQRLEEEELEVLPRLREGAAHLGQQSAHLAELIAELEGRCQLPALGLLQDIKDALRRVQDVKLQPPEVVPMELRTVCRVPGLVETLRRFRGDVTLDPDTANPELILSEDRRSVQRGDLRQALPDSPERFDPGPCVLGQERFTSGRHYWEVEVGDRTSWALGVCRENVNRKEKGELSAGNGFWILVFLGSYYNSSERALAPLRDPPRRVGIFLDYEAGHLSFYSATDGSLLFIFPEIPFSGTLRPLFSPLSSSPTPMTICRPKGGSGDTLAPQ";
    //       Exephrin5
    String Exephrin5Uniprot = "P52803";
    String Exerphin5Sequence = "MLHVEMLTLVFLVLWMCVFSQDPGSKAVADRYAVYWNSSNPRFQRGDYHIDVCINDYLDVFCPHYEDSVPEDKTERYVLYMVNFDGYSACDHTSKGFKRWECNRPHSPNGPLKFSEKFQLFTPFSLGFEFRPGREYFYISSAIPDNGRRSCLKLKVFVRPTNSCMKTIGVHDRVFDVNDKVENSLEPADDTVHESAEPSRGENAAQTPRIPSRLLAILLFLLAMLLTL";
    //       APOE
    String APOEUniport = "P02649";
    String APOESequence = "MKVLWAALLVTFLAGCQAKVEQAVETEPEPELRQQTEWQSGQRWELALGRFWDYLRWVQTLSEQVQEELLSSQVTQELRALMDETMKELKAYKSELEEQLTPVAEETRARLSKELQAAQARLGADMEDVCGRLVQYRGEVQAMLGQSTEELRVRLASHLRKLRKRLLRDADDLQKRLAVYQAGAREGAERGLSAIRERLGPLVEQGRVRAATVGSLAGQPLQERAQAWGERLRARMEEMGSRTRDRLDEVKEQVAEVRAKLEEQAQQIRLQAEAFQARLKSWFEPLVEDMQRQWAGLVEKVQAAVGTSAAPVPSDNH";
    //      BACE1
    String BACE1Uniprot = "P56817";
    String BACE1Sequence = "MAQALPWLLLWMGAGVLPAHGTQHGIRLPLRSGLGGAPLGLRLPRETDEEPEEPGRRGSFVEMVDNLRGKSGQGYYVEMTVGSPPQTLNILVDTGSSNFAVGAAPHPFLHRYYQRQLSSTYRDLRKGVYVPYTQGKWEGELGTDLVSIPHGPNVTVRANIAAITESDKFFINGSNWEGILGLAYAEIARPDDSLEPFFDSLVKQTHVPNLFSLQLCGAGFPLNQSEVLASVGGSMIIGGIDHSLYTGSLWYTPIRREWYYEVIIVRVEINGQDLKMDCKEYNYDKSIVDSGTTNLRLPKKVFEAAVKSIKAASSTEKFPDGFWLGEQLVCWQAGTTPWNIFPVISLYLMGEVTNQSFRITILPQQYLRPVEDVATSQDDCYKFAISQSSTGTVMGAVIMEGFYVVFDRARKRIGFAVSACHVHDEFRTAAVEGPFVTLDMEDCGYNIPQTDESTLMTIAYVMAAICALFMLPLCLMVCQWRCLRCLRQQHDDFADDISLLK";
    String putreq1 = String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", TauUniprot, TauSequence);
    String putreq2 = String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", AmyloidBetaUniprot, AmyloidBetaSquence);
    String putreq3 = String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", TRIM11Uniport, TRIM11Sequence);
    String putreq4 = String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", Exephrin5Uniprot, Exerphin5Sequence);
    String putreq5 = String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", APOEUniport, APOESequence);
    String putreq6 = String.format("{TYPE:PUT,KEY:%s,VALUE:%s}", BACE1Uniprot, BACE1Sequence);
    String getreq1 = String.format("{TYPE:GET,KEY:%s}", TauUniprot);
    String getreq2 = String.format("{TYPE:GET,KEY:%s}", AmyloidBetaUniprot);
    String getreq3 = String.format("{TYPE:GET,KEY:%s}", TRIM11Uniport);
    String getreq4 = String.format("{TYPE:GET,KEY:%s}", Exephrin5Uniprot);
    String getreq5 = String.format("{TYPE:GET,KEY:%s}", APOEUniport);
    String getreq6 = String.format("{TYPE:GET,KEY:%s}", BACE1Uniprot);
    String deletereq1 = String.format("{TYPE:DELETE,KEY:%s}", TauUniprot);
    String deletereq2 = String.format("{TYPE:DELETE,KEY:%s}", AmyloidBetaUniprot);
      String deletereq3 = String.format("{TYPE:DELETE,KEY:%s}", TRIM11Uniport);
    String deletereq4 = String.format("{TYPE:DELETE,KEY:%s}", Exephrin5Uniprot);
    String deletereq5 = String.format("{TYPE:DELETE,KEY:%s}", APOEUniport);
    String deletereq6 = String.format("{TYPE:DELETE,KEY:%s}", BACE1Uniprot);
    private BaseServer stub;

    public Example(BaseServer stub) {
        setStub(stub);
    }

    public BaseServer getStub() {
        return stub;
    }

    public void setStub(BaseServer stub) {
        this.stub = stub;
    }

    public void runExample() {
        Packet putPacket1 = new Packet(this.putreq1);
        Packet putPacket2 = new Packet(this.putreq2);
        Packet putPacket3 = new Packet(this.putreq3);
        Packet putPacket4 = new Packet(this.putreq4);
        Packet putPacket5 = new Packet(this.putreq5);
        Packet putPacket6 = new Packet(this.putreq6);


        Packet getPacket1 = new Packet(this.getreq1);
        Packet getPacket2 = new Packet(this.getreq2);
        Packet getPacket3 = new Packet(this.getreq3);
        Packet getPacket4 = new Packet(this.getreq4);
        Packet getPacket5 = new Packet(this.getreq5);
        Packet getPacket6 = new Packet(this.getreq6);

        Packet deletePacket1 = new Packet(this.deletereq1);
        Packet deletePacket2 = new Packet(this.deletereq2);
        Packet deletePacket3 = new Packet(this.deletereq3);
        Packet deletePacket4 = new Packet(this.deletereq4);
        Packet deletePacket5 = new Packet(this.deletereq5);
        Packet deletePacket6 = new Packet(this.deletereq6);
        stub = this.getStub();
        try {
            stub.Put(putPacket1).logResponseClient();
            stub.Put(putPacket2).logResponseClient();
            stub.Put(putPacket3).logResponseClient();
            stub.Put(putPacket4).logResponseClient();
            stub.Put(putPacket5).logResponseClient();
            stub.Put(putPacket6).logResponseClient();


            stub.Get(getPacket1).logResponseClient();
            stub.Get(getPacket2).logResponseClient();
            stub.Get(getPacket3).logResponseClient();
            stub.Get(getPacket4).logResponseClient();
            stub.Get(getPacket5).logResponseClient();
            stub.Get(getPacket6).logResponseClient();


            stub.Delete(deletePacket1).logResponseClient();
            stub.Delete(deletePacket2).logResponseClient();
            stub.Delete(deletePacket3).logResponseClient();
            stub.Delete(deletePacket4).logResponseClient();
            stub.Delete(deletePacket5).logResponseClient();
            stub.Delete(deletePacket6).logResponseClient();
        } catch (RemoteException e) {
            System.err.println("The connection to the sever failed ");
            e.printStackTrace();
        }


    }


}

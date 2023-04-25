// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// VM
//    HW = memória, cpu
//    SW = tratamento int e chamada de sistema
// Funcionalidades de carga, execução e dump de memória

import java.util.*;

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public class Memory {
		public int tamMem;
		public Word[] m; // m representa a memória fisica: um array de posicoes de memoria (word)

		public Memory(int tamMem) {
			this.tamMem = tamMem;
			this.m = new Word[tamMem];
			for (int i = 0; i < tamMem; i++) {
				m[i] = new Word(Opcode.___, -1, -1, -1);
			}
			;
		}

		public void dump(Word w) { // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
			System.out.print("[ ");
			System.out.print(w.opc);
			System.out.print(", ");
			System.out.print(w.r1);
			System.out.print(", ");
			System.out.print(w.r2);
			System.out.print(", ");
			System.out.print(w.p);
			System.out.println("  ] ");
		}

		public void dump(int ini, int fim) {
			for (int i = ini; i < fim; i++) {
				System.out.print(i);
				System.out.print(":  ");
				dump(m[i]);
			}
		}
	}

	// -------------------------------------------------------------------------------------------------------

	public class Word { // cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; //
		public int r1; // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int r2; // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _r1, int _r2, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			r1 = _r1;
			r2 = _r2;
			p = _p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public enum Opcode {
		DATA, ___, // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios e parada
		JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT, // matematicos
		LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
		TRAP // chamada de sistema
	}

	public enum Interrupts { // possiveis interrupcoes que esta CPU gera
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intSTOP;
	}

	public class CPU {
		private boolean traceOn = true; // se true, mostra cada instrucao em execucao
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
		// característica do processador: contexto da CPU ...
		private int pc; // ... composto de program counter,
		private Word ir; // instruction register,
		private int[] reg; // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		private int base; // base e limite de acesso na memoria
		private int limite; // por enquanto toda memoria pode ser acessada pelo processo rodando
							// ATE AQUI: contexto da CPU - tudo que precisa sobre o estado de um processo
							// para executa-lo
							// nas proximas versoes isto pode modificar

		private Memory mem; // mem tem funcoes de dump e o array m de memória 'fisica'
		private Word[] m; // CPU acessa MEMORIA, guarda referencia a 'm'. m nao muda. semre será um array
							// de palavras

		private InterruptHandling ih; // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema - trap
		private boolean debug; // se true entao mostra cada instrucao em execucao

		public CPU(Memory _mem, InterruptHandling _ih, SysCallHandling _sysCall, boolean _debug) { // ref a MEMORIA e
																									// interrupt handler
																									// passada na
																									// criacao da CPU
			maxInt = 32767; // capacidade de representacao modelada
			minInt = -32767; // se exceder deve gerar interrupcao de overflow
			mem = _mem; // usa mem para acessar funcoes auxiliares (dump)
			m = mem.m; // usa o atributo 'm' para acessar a memoria.
			reg = new int[10]; // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO
			ih = _ih; // aponta para rotinas de tratamento de int
			sysCall = _sysCall; // aponta para rotinas de tratamento de chamadas de sistema
			debug = _debug; // se true, print da instrucao em execucao
		}

		private boolean legal(int e) { // todo acesso a memoria tem que ser verificado
			// ????
			return true;
		}

		private boolean testOverflow(int v) { // toda operacao matematica deve avaliar se ocorre overflow
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;
				return false;
			}
			;
			return true;
		}

		private boolean testInvalidAddress(int e) { // toda operacao de acesso a memoria deve avaliar se endereco eh
													// valido
			if (e > mem.tamMem) {
				irpt = Interrupts.intEnderecoInvalido;
				return false;
			}
			return true;
		}

		private boolean testInvalidInstruction(Word w) { // toda instrucao deve ser verificada
			if ((w.r1 > reg.length || w.r1 < -1) || (w.r2 > reg.length || w.r2 < -1)) {

				irpt = Interrupts.intEnderecoInvalido;
				return false;
			} else {
				Opcode[] op = Opcode.values();
				for (Opcode operation : op) {
					if (w.opc == Opcode.___) {
						break;
					} else if (w.opc == operation) {
						return true;
					}
				}
				irpt = Interrupts.intInstrucaoInvalida;
			}
			return true;
		}

		private boolean testStop() { // toda instrucao deve ser verificada
			if (ir.opc == Opcode.STOP) {
				irpt = Interrupts.intSTOP;
				return true;
			};
			return false;
		}

		public void setContext(int _base, int _limite, int _pc) { // no futuro esta funcao vai ter que ser
			base = _base; // expandida para setar todo contexto de execucao,
			limite = _limite; // agora, setamos somente os registradores base,
			pc = _pc; // limite e pc (deve ser zero nesta versao)
			irpt = Interrupts.noInterrupt; // reset da interrupcao registrada
		}

		public void run(Word[] memory) { // execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente
							// setado
			while (true) { // ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
				// --------------------------------------------------------------------------------------------------
				// FETCH
				if (legal(pc)) { // pc valido
					ir = m[pc]; // <<<<<<<<<<<< busca posicao da memoria apontada por pc, guarda em ir
					if (debug && vm.cpu.traceOn) {
						System.out.print("                               pc: " + pc + "       exec: ");
						mem.dump(ir);
					}
					// --------------------------------------------------------------------------------------------------
					// EXECUTA INSTRUCAO NO ir
					switch (ir.opc) { // conforme o opcode (código de operação) executa

						// Instrucoes de Busca e Armazenamento em Memoria
						case LDI: // Rd ← k
							testInvalidAddress(ir.r1);
							testInvalidInstruction(m[ir.p]);
							reg[ir.r1] = ir.p;
							pc++;
							break;

						case LDD: // Rd <- [A]
							if (legal(ir.p)) {
								testInvalidAddress(ir.p);
								testInvalidAddress(ir.r1);
								testInvalidInstruction(m[ir.p]);
								reg[ir.r1] = m[ir.p].p;
								pc++;
							}
							break;

						case LDX: // RD <- [RS] // NOVA
							if (legal(reg[ir.r2])) {
								testInvalidAddress(ir.r1);
								testInvalidAddress(ir.r2);
								testInvalidInstruction(m[ir.r2]);
								reg[ir.r1] = m[reg[ir.r2]].p;
								pc++;
							}
							break;

						case STD: // [A] ← Rs
							if (legal(ir.p)) {
								testInvalidAddress(ir.p);
								testInvalidAddress(ir.r1);
								testInvalidInstruction(m[ir.r1]);
								testInvalidInstruction(m[ir.p]);
								m[ir.p].opc = Opcode.DATA;
								m[ir.p].p = reg[ir.r1];
								pc++;
							}
							;
							break;

						case STX: // [Rd] ←Rs
							if (legal(reg[ir.r1])) {
								testInvalidAddress(ir.r1);
								testInvalidAddress(ir.r2);
								testInvalidInstruction(m[ir.r1]);
								testInvalidInstruction(m[ir.r2]);
								m[reg[ir.r1]].opc = Opcode.DATA;
								m[reg[ir.r1]].p = reg[ir.r2];
								pc++;
							}
							;
							break;

						case MOVE: // RD <- RS
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.r2);
							testInvalidInstruction(m[ir.r1]);
							testInvalidInstruction(m[ir.r2]);
							reg[ir.r1] = reg[ir.r2];
							pc++;
							break;

						// Instrucoes Aritmeticas
						case ADD: // Rd ← Rd + Rs
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.r2);
							testInvalidInstruction(m[ir.r1]);
							testInvalidInstruction(m[ir.r2]);
							reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case ADDI: // Rd ← Rd + k
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.p);
							testInvalidInstruction(m[ir.r1]);
							testInvalidInstruction(m[ir.p]);
							reg[ir.r1] = reg[ir.r1] + ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case SUB: // Rd ← Rd - Rs
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.r2);
							testInvalidInstruction(m[ir.r1]);
							testInvalidInstruction(m[ir.r2]);
							reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case SUBI: // RD <- RD - k // NOVA
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.p);
							testInvalidInstruction(m[ir.r1]);
							testInvalidInstruction(m[ir.p]);
							reg[ir.r1] = reg[ir.r1] - ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case MULT: // Rd <- Rd * Rs
							testInvalidAddress(ir.r1);
							testInvalidInstruction(m[ir.r1]);
							reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						// Instrucoes JUMP
						case JMP: // PC <- k
							testInvalidAddress(ir.p);
							pc = ir.p;
							break;

						case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.r2);
							if (reg[ir.r2] > 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIGK: // If RC > 0 then PC <- k else PC++
							testInvalidAddress(ir.r2);
							if (reg[ir.r2] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPILK: // If RC < 0 then PC <- k else PC++
							testInvalidAddress(ir.r2);
							if (reg[ir.r2] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPIEK: // If RC = 0 then PC <- k else PC++
							testInvalidAddress(ir.r2);
							if (reg[ir.r2] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.r2);
							if (reg[ir.r2] < 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.r2);
							if (reg[ir.r2] == 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIM: // PC <- [A]
							testInvalidAddress(ir.p);
							pc = m[ir.p].p;
							break;

						case JMPIGM: // If RC > 0 then PC <- [A] else PC++
							testInvalidAddress(ir.r2);
							testInvalidAddress(ir.p);
							if (reg[ir.r2] > 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPILM: // If RC < 0 then PC <- k else PC++
							testInvalidAddress(ir.r2);
							testInvalidAddress(ir.p);
							if (reg[ir.r2] < 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPIEM: // If RC = 0 then PC <- k else PC++
							testInvalidAddress(ir.r2);
							testInvalidAddress(ir.p);
							if (reg[ir.r2] == 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPIGT: // If RS>RC then PC <- k else PC++
							testInvalidAddress(ir.r1);
							testInvalidAddress(ir.r2);
							if (reg[ir.r1] > reg[ir.r2]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						// outras
						case STOP: // por enquanto, para execucao
							testStop();
							irpt = Interrupts.intSTOP;
							break;

						case DATA:

							irpt = Interrupts.intInstrucaoInvalida;
							break;

						// Chamada de sistema
						case TRAP:
							sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
							if (reg[8] == 1) {
								Scanner in = new Scanner(System.in); // temos IO
								System.out.println("INPUT DATA: ");
								// int c = in.nextInt();
								int c = 50;

								int posicao = reg[9];

								m[posicao].opc = Opcode.DATA;
								m[posicao].p = c;

								in.close();
							} else if (reg[8] == 2) {
								int posicao = reg[9];
								System.out.println("OUTPUT DATA [posicao = " + posicao + "]: " + m[posicao].p);
							}

							pc++;
							break;

						// Inexistente
						default:
							testInvalidInstruction(m[ir.p]);
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}
				}
				// --------------------------------------------------------------------------------------------------
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (!(irpt == Interrupts.noInterrupt)) { // existe interrupção
					ih.handle(irpt, pc); // desvia para rotina de tratamento
					break; // break sai do loop da cpu
				}
			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}
	}
	// ------------------ C P U - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ------------------- V M - constituida de CPU e MEMORIA
	// -----------------------------------------------
	// -------------------------- atributos e construcao da VM
	// -----------------------------------------------
	public class VM {
		public int tamMem;
		public Word[] m;
		public Memory mem;
		public CPU cpu;
		public GM gm;
		public GP gp;

		public VM(InterruptHandling ih, SysCallHandling sysCall) {
			// vm deve ser configurada com endereço de tratamento de interrupcoes e de
			// chamadas de sistema
			// cria memória
			gp = new GP();
			tamMem = 1024;
			int tamPag = 8;
			mem = new Memory(tamMem);
			m = mem.m;
			gm = new GM(tamMem, tamPag);
			// cria cpu
			cpu = new CPU(mem, ih, sysCall, true); // true liga debug
		}

	}
	// ------------------- V M - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// --------------------H A R D W A R E - fim
	// -------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S O F T W A R E - inicio
	// ----------------------------------------------------------

	// ------------------- G M - Gerente de Memória
	// -----------------------------------------------
	// -------------------------- frames da memória
	// -----------------------------------------------

	public class GM {
		private int tamMem;
		private int tamFrame;
		private static Map<Integer, Word[]> listaFrames;
		private static int ultimoFrameAlocado;

		public GM(int tamMem, int tamPag) {
			this.tamMem = tamMem;
			this.tamFrame = tamPag;
			ultimoFrameAlocado = 0;
			listaFrames = new HashMap<Integer, Word[]>(tamMem / tamPag);
		}

		public Map<Integer, Integer> aloca(Word[] process, Word[] memory) {

			int nroPaginas = (process.length % tamFrame == 0) ? (process.length / tamFrame)
					: ((process.length / tamFrame) + 1);
			int framesAlocados = listaFrames.size();
			if (framesAlocados + nroPaginas > tamMem / tamFrame) {
				System.out.println("Memoria insuficiente");
				return null;
			}

			Map<Integer, Integer> tabelaPagina = new HashMap<Integer, Integer>(nroPaginas);

			int instrucoesAlocadas = 0;
			do {
				// Se o ultimo frame alocado for o ultimo do vetor, volta para o primeiro
				if (ultimoFrameAlocado == listaFrames.size()) {
					ultimoFrameAlocado = 0;
				}

				// Se o frame atual estiver ocupado, pula para o proximo
				if (listaFrames.get(ultimoFrameAlocado) != null) {
					ultimoFrameAlocado++;
				}

				// Se o frame atual estiver livre, aloca o processo
				if (listaFrames.get(ultimoFrameAlocado) == null) {

					// Aloca 8 paginas do processo por vez (1 frame)
					Word[] instrucoes = new Word[tamFrame];
					for (int i = instrucoesAlocadas; i < instrucoesAlocadas + tamFrame; i++) {
						if (i < process.length) {
							instrucoes[i % tamFrame] = process[i];
						}
					}

					carga(instrucoesAlocadas, process, memory, nroPaginas, instrucoes, tabelaPagina);

					instrucoesAlocadas += 8;
					instrucoes = null;
				}
			} while (instrucoesAlocadas < process.length);

			return tabelaPagina;
		}

		
		// Após o GM alocar os frames, devolvendo a tabelaPaginas, deve-se proceder a
		// carga
		// cada pagina i do programa deve ser copiada (exatamente como tal) para o frame
		// informado em tabela páginas
		public Map<Integer, Integer> carga(int instrucoesAlocadas, Word[] process, Word[] memory, int nroPaginas,
				Word[] instrucoes, Map<Integer, Integer> tabelaPagina) {

			for (int i = instrucoesAlocadas; i < instrucoesAlocadas + tamFrame; i++) {
				
				int posicaoDoFrameNaMemoria = ultimoFrameAlocado * tamFrame + (i % tamFrame);

				if (i < process.length) {
					memory[posicaoDoFrameNaMemoria].opc = process[i].opc; // A memoria recebe o processo
					memory[posicaoDoFrameNaMemoria].r1 = process[i].r1;
					memory[posicaoDoFrameNaMemoria].r2 = process[i].r2;
					memory[posicaoDoFrameNaMemoria].p = process[i].p;

					listaFrames.put(ultimoFrameAlocado, instrucoes); // A tabela de frame recebe o endereco da memoria
					
					tabelaPagina.put(i, ultimoFrameAlocado);
				}
			}
			
			return tabelaPagina;
			
		}

		public void desaloca(Map<Integer, Integer> tabelaPagina) {
			for (Integer frame : tabelaPagina.values()) {
				listaFrames.remove(frame);
			}
		}
	}

	// ------------------- G M - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ------------------- G P - Gerente de Processos
	// -----------------------------------------------
	// --------------------------
	// -----------------------------------------------

	public class GP {
		private static List<PCB> listaRunning;
		private static List<PCB> listaReady;
		private static List<PCB> listaBlocked;
		private static List<PCB> listaProcess;
		private static int id;

		public GP() {
			listaRunning = new ArrayList<PCB>();
			listaReady = new ArrayList<PCB>();
			listaBlocked = new ArrayList<PCB>();
			listaProcess = new ArrayList<PCB>();
			id = 0;
		}

		public PCB criaProcesso(Word[] programa) {
			Map<Integer, Integer> tabelaPaginasPrograma = vm.gm.aloca(programa, vm.m); // carga do programa na
			// memoria

			if (tabelaPaginasPrograma == null) {
				// sem espaço
				return null;
			}
			if(vm.cpu.traceOn){
				System.out.println("---------------------------------- tabela de paginas do programa");
				System.out.println(tabelaPaginasPrograma);
				System.out.println("\n---------------------------------- programa carregado na memoria");
			}


			int primeiroFramePrograma = tabelaPaginasPrograma.get(0); // pega o primeiro frame do programa
			int endereçoNaMemoria = (primeiroFramePrograma * vm.gm.tamFrame); // calcula o endereço na memoria

			Opcode op = vm.m[endereçoNaMemoria].opc; // pega opcode da primeira instrucao
			int r1 = vm.m[endereçoNaMemoria].r1; // pega r1 da primeira instrucao
			int r2 = vm.m[endereçoNaMemoria].r2; // pega r2 da primeira instrucao
			int p = vm.m[endereçoNaMemoria].p; // pega p da primeira instrucao

			int[] reg = { r1, r2, p };

			PCB pcb = new PCB(id, "ready", reg, 0, tabelaPaginasPrograma); // cria PCB do processo

			Set<Integer> frames = new HashSet<Integer>();
			for(int i = 0; i < pcb.getTabelaPaginas().values().size(); i++) {
				frames.add((Integer) pcb.getTabelaPaginas().values().toArray()[i]);
			}
			
			if(vm.cpu.traceOn){
				dump(frames);
			}

			pcb.setMemory(new Word[frames.size() * vm.gm.tamFrame]); // cria memoria do processo

			Word[] memoriaProcesso = pcb.getMemory(); // pega memoria do processo
			for(int i = 0; i < tabelaPaginasPrograma.values().size(); i++) {
				memoriaProcesso[i] = vm.m[tabelaPaginasPrograma.get(i) * vm.gm.tamFrame + i];
			}
			

			id++; // incrementa id
			listaProcess.add(pcb); // adiciona PCB na lista de processos
			listaReady.add(pcb); // adiciona PCB na lista de prontos

			return pcb;
		}

		
		//executa <id> - executa o processo com id fornecido. se não houver processo, retorna erro.
		public void executa(int id){
			int[] index = new int[1];
			listaProcess.forEach(p -> {
				if (p.getProcessId() == id) {
					index[0] = listaReady.indexOf(p);
				}
			});
			
			PCB pcb = listaProcess.get(index[0]);
			
			if (pcb == null) {
				return;
			}
			
			
			if(vm.cpu.traceOn){
				System.out.println("---------------------------------- inicia execucao ");
			}

			vm.cpu.setContext(0, vm.tamMem - 1, pcb.getTabelaPaginas().get(0)); // seta estado da cpu
			
			listaReady.get(index[0]).setProcessState("running"); // seta estado do processo para running
			listaReady.remove(index[0]); // remove processo da lista de processos prontos
			listaRunning.add(pcb); // adiciona processo a lista de processos em execucao
			
			vm.cpu.run(pcb.getMemory()); // cpu roda programa ate parar
		}

		public Map<Integer, Integer> desaloca(int id) {
			int[] index = new int[1];
			listaProcess.forEach(p -> {
				if (p.getProcessId() == id) {
					index[0] = listaProcess.indexOf(p);
				}
			});
	
			PCB pcb = listaProcess.get(index[0]);
	
			if (pcb == null) {
				return null;
			}
			
			Set<Integer> frames = new HashSet<Integer>();
			for(int i = 0; i < pcb.getTabelaPaginas().values().size(); i++) {
				frames.add((Integer) pcb.getTabelaPaginas().values().toArray()[i]);
			}
	
			
			if(vm.cpu.traceOn){
				for(Integer frame : frames) {
					System.out.println("frame: " + frame);
				}

				System.out.println("---------------------------------- memoria do processo após execucao");
				dump(frames);
			}
	
			vm.gm.desaloca(pcb.getTabelaPaginas());
			listaRunning.get(index[0]).setProcessState("finished"); // seta estado do processo para finished
			listaRunning.remove(index[0]); // remove processo da lista de processos em execucao
			listaProcess.remove(index[0]); // remove processo da lista de processos
	
			return pcb.getTabelaPaginas();
		}
	}

	public class PCB {
		private int processId; // identificação do processo
		private String processState; // estado do processo
		private int r1;
		private int r2;
		private int p;
		private int pc; // contador do programa
		private Map<Integer, Integer> tabelaPaginas; // informações de gerenciamento de memória
		private Word[] memory;

		public PCB(int processId, String processState, int[] reg, int pc, Map<Integer, Integer> tabelaPaginas) {
			this.processId = processId;
			this.processState = processState;
			this.r1 = reg[0];
			this.r2 = reg[1];
			this.p = reg[2];
			this.pc = pc;
			this.tabelaPaginas = tabelaPaginas;
		}

		public Word[] getMemory(){
			return memory;
		}

		public void setMemory(Word[] memory){
			this.memory = memory;
		}

		public int getProcessId() {
			return processId;
		}

		public void setProcessId(int processId) {
			this.processId = processId;
		}

		public String getProcessState() {
			return processState;
		}

		public void setProcessState(String processState) {
			this.processState = processState;
		}

		public int[] getReg() {
			return new int[] { r1, r2, p };
		}

		public int getPc() {
			return pc;
		}

		public void setPc(int pc) {
			this.pc = pc;
		}

		public Map<Integer, Integer> getTabelaPaginas() {
			return tabelaPaginas;
		}

	}

	// ------------------- G P - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	// ----------------------------------
	public class InterruptHandling {
		public void handle(Interrupts irpt, int pc) { // apenas avisa - todas interrupcoes neste momento finalizam o
														// programa
			if(vm.cpu.traceOn){
				System.out
						.println("                                               Interrupcao " + irpt + "   pc: " + pc);
			}
		}
	}

	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
		private VM vm;

		public void setVM(VM _vm) {
			vm = _vm;
		}

		public void handle() { // apenas avisa - todas interrupcoes neste momento finalizam o programa
			if(vm.cpu.traceOn){
				System.out.println("                                               Chamada de Sistema com op  /  par:  "
						+ vm.cpu.reg[8] + " / " + vm.cpu.reg[9]);
			}
		}
	}

	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	private int load(Word[] p) {
		PCB pcbProcesso = vm.gp.criaProcesso(p);
		return pcbProcesso.getProcessId();
	}

	private void executa(int id){
		vm.gp.executa(id);
	}

	private void desaloca(int id){
		vm.gp.desaloca(id);
	}

	//exit - sai do sistema
	private void exit(){
		System.exit(0);
	}

	// dump <id> - lista o conteúdo do PCB e o conteúdo da partição de memória do processo com id
	public void dump(Collection<Integer> frames) {
		
			for (Integer frame : frames) {
				System.out.println("frame: " + frame);
				System.out.println("inicio: " + (frame * vm.gm.tamFrame));
				System.out.println("fim: " + ((frame + 1) * vm.gm.tamFrame - 1));
				vm.mem.dump((frame * vm.gm.tamFrame), ((frame + 1) * vm.gm.tamFrame));// dump da memoria com resultado
			}
		
	}

	// dumpM <inicio, fim> - lista a memória entre posições início e fim, independente do processo
	public void dumpM(int inicio, int fim){
		vm.mem.dump(inicio, fim);
	}

	public void traceOn() {
		vm.cpu.traceOn = true;
	}

	public void traceOff() {
		vm.cpu.traceOn = false;
	}

	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	public VM vm;
	public InterruptHandling ih;
	public SysCallHandling sysCall;
	public static Programas progs;

	public Sistema() { // a VM com tratamento de interrupções
		ih = new InterruptHandling();
		sysCall = new SysCallHandling();
		vm = new VM(ih, sysCall);
		sysCall.setVM(vm);
		progs = new Programas();
	}

	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {
		
		Sistema s = new Sistema();
		s.traceOn();
		int opcao = 42;
		while(opcao != -1){
			System.out.println("------------------------------------");
			System.out.println("       Digite o comando: ");
			System.out.println("       1 - load");
			System.out.println("       2 - executa");
			System.out.println("       3 - desaloca");
			System.out.println("       4 - exit");
			System.out.println("       5 - dump");
			System.out.println("       6 - dumpM");
			System.out.println("       7 - traceOn");
			System.out.println("       8 - traceOff");
			System.out.println("------------------------------------");
			System.out.println("\n\n");

			Scanner scanner = new Scanner(System.in);
			opcao = scanner.nextInt();

			switch(opcao) {
				case 1:
					System.out.println("------------------------------------");
					System.out.println("		 Digite o programa: ");
					System.out.println("       1 - fibonacci10");
					System.out.println("       2 - progMinimo");
					System.out.println("       3 - entrada");
					System.out.println("       4 - saida");
					System.out.println("       5 - io");
					System.out.println("       6 - fatorial");
					System.out.println("       7 - fibonacciTRAP");
					System.out.println("       8 - fatorialTRAP");
					System.out.println("------------------------------------");
					System.out.println("\n\n");

					int programa = scanner.nextInt();
					switch(programa){
						case 1:
							s.load(progs.fibonacci10);
							break;
						case 2:
							s.load(progs.progMinimo);
							break;
						case 3:
							s.load(progs.entrada);
							break;
						case 4:
							s.load(progs.saida);
							break;
						case 5:
							s.load(progs.io);
							break;
						case 6:
							s.load(progs.fatorial);
							break;
						case 7:
							s.load(progs.fibonacciTRAP);
							break;
						case 8:
							s.load(progs.fatorialTRAP);
							break;
						default:
							System.out.println("Programa não encontrado");
							break;	
					}
					System.out.println("------------------------------------");
					System.out.println("\n\n");
					break;
				case 2:
					System.out.println("Digite o id do programa: ");
					int id = scanner.nextInt();
					s.executa(id);
					System.out.println("------------------------------------");
					System.out.println("\n\n");
					break;
				case 3:
					System.out.println("Digite o id do programa: ");
					int id2 = scanner.nextInt();
					s.desaloca(id2);
					System.out.println("------------------------------------");
					System.out.println("\n\n");
					break;
				case 4:
					s.exit();
					break;
				case 5:
					System.out.println("------------------------------------");
					System.out.println("Digite o id do programa: ");
					System.out.println("\n\n");
					int id3 = scanner.nextInt();
					
					int[] index = new int[1];
					s.vm.gp.listaProcess.forEach(p -> {
						if (p.getProcessId() == id3) {
							index[0] = s.vm.gp.listaProcess.indexOf(p);
						}
					});
			
					PCB pcb = s.vm.gp.listaProcess.get(index[0]);
			
					if (pcb == null) {
						System.out.println("Processo não encontrado");
					}
					
					Set<Integer> frames = new HashSet<Integer>();
					for(int i = 0; i < pcb.getTabelaPaginas().values().size(); i++) {
						frames.add((Integer) pcb.getTabelaPaginas().values().toArray()[i]);
					}

					s.dump(frames);
					System.out.println("------------------------------------");
					System.out.println("\n\n");
					break;
				case 6:
					System.out.println("------------------------------------");
					System.out.println("Digite o inicio: ");
					int inicio = scanner.nextInt();
					System.out.println("Digite o fim: ");
					int fim = scanner.nextInt();
					s.dumpM(inicio, fim);
					System.out.println("------------------------------------");
					System.out.println("\n\n");
					break;
				case 7:
					s.traceOn();
					break;
				case 8:
					s.traceOff();
					break;
				default:
					System.out.println("Comando não encontrado");
					break;
			}
		}


		// s.loadAndExec(progs.fibonacci10);
		// s.loadAndExec(progs.progMinimo);
		// s.loadAndExec(progs.entrada);
		// s.loadAndExec(progs.saida);
		int id = s.load(progs.io);
		int id2= s.load(progs.fatorial);
		
		s.executa(id);
		s.executa(id2);

		s.desaloca(id);
		s.desaloca(id2);
		// s.loadAndExec(progs.fatorialTRAP); // saida
		// s.loadAndExec(progs.fibonacciTRAP); // entrada
		// s.loadAndExec(progs.PC); // bubble sort
		//scanner.close();
		s.exit();

	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

	public class Programas {

		public Word[] entrada = new Word[] {
				new Word(Opcode.LDI, 8, -1, 1),
				new Word(Opcode.LDI, 9, -1, 10),
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
		};

		public Word[] saida = new Word[] {
				new Word(Opcode.LDI, 8, -1, 2),
				new Word(Opcode.LDI, 9, -1, 10),
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
		};

		public Word[] io = new Word[] {
				new Word(Opcode.LDI, 8, -1, 1),
				new Word(Opcode.LDI, 9, -1, 10),
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.LDI, 8, -1, 2),
				new Word(Opcode.LDI, 9, -1, 10),
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
		};

		public Word[] fatorial = new Word[] {
				// este fatorial so aceita valores positivos. nao pode ser zero
				// linha coment
				new Word(Opcode.LDI, 0, -1, 5), // 0 r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1), // 6 decrementa r0 1
				new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
				new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
				new Word(Opcode.STOP, -1, -1, -1), // 9 stop
				new Word(Opcode.DATA, -1, -1, -1) }; // 10 ao final o valor do fatorial estará na posição 10 da
														// memória

		public Word[] progMinimo = new Word[] {
				new Word(Opcode.LDI, 0, -1, 999),
				new Word(Opcode.STD, 0, -1, 10),
				new Word(Opcode.STD, 0, -1, 11),
				new Word(Opcode.STD, 0, -1, 12),
				new Word(Opcode.STD, 0, -1, 13),
				new Word(Opcode.STD, 0, -1, 14),
				new Word(Opcode.STOP, -1, -1, -1) };

		public Word[] fibonacci10 = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 20),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 21),
				new Word(Opcode.LDI, 0, -1, 22),
				new Word(Opcode.LDI, 6, -1, 6),
				new Word(Opcode.LDI, 7, -1, 31),
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 20
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) }; // ate aqui - serie de fibonacci ficara armazenada

		public Word[] fatorialTRAP = new Word[] {
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 18),
				new Word(Opcode.LDI, 8, -1, 2), // escrita
				new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1), // POS 17
				new Word(Opcode.DATA, -1, -1, -1) // POS 18
		};

		public Word[] fibonacciTRAP = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 8, -1, 1), // leitura
				new Word(Opcode.LDI, 9, -1, 100), // endereco a guardar
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.LDD, 7, -1, 100), // numero do tamanho do fib
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 7, -1),
				new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
				new Word(Opcode.LDI, 1, -1, -1), // caso negativo
				new Word(Opcode.STD, 1, -1, 41),
				new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
				new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
				new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de fibonacci gerada
				new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.ADDI, 3, -1, 1),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 42),
				new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.LDI, 0, -1, 43),
				new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
				new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
				new Word(Opcode.ADD, 5, 7, -1),
				new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
				new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
				new Word(Opcode.STOP, -1, -1, -1), // POS 36
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 41
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1)
		};

		public Word[] PB = new Word[] {
				// dado um inteiro em alguma posição de memória,
				// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
				// número na saída
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 15),
				new Word(Opcode.STOP, -1, -1, -1), // POS 14
				new Word(Opcode.DATA, -1, -1, -1) }; // POS 15

		public Word[] PC = new Word[] {
				// Para um N definido (10 por exemplo)
				// o programa ordena um vetor de N números em alguma posição de memória;
				// ordena usando bubble sort
				// loop ate que não swap nada
				// passando pelos N valores
				// faz swap de vizinhos se da esquerda maior que da direita
				new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
				new Word(Opcode.LDI, 6, -1, 5), // aux N
				new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
				new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
				new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
				new Word(Opcode.STD, 0, -1, 46),
				new Word(Opcode.LDI, 0, -1, 3),
				new Word(Opcode.STD, 0, -1, 47),
				new Word(Opcode.LDI, 0, -1, 5),
				new Word(Opcode.STD, 0, -1, 48),
				new Word(Opcode.LDI, 0, -1, 1),
				new Word(Opcode.STD, 0, -1, 49),
				new Word(Opcode.LDI, 0, -1, 2),
				new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
				new Word(Opcode.STD, 3, -1, 99),
				new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
				new Word(Opcode.STD, 3, -1, 98),
				new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
				new Word(Opcode.STD, 3, -1, 97),
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
				new Word(Opcode.STD, 3, -1, 96),
				new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
				new Word(Opcode.ADD, 6, 7, -1),
				new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
				new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o
													// loop
													// de vez do programa
				new Word(Opcode.LDX, 0, 5, -1), // r0 e r1 pegando valores das posições da memoria POS 26
				new Word(Opcode.LDX, 1, 4, -1),
				new Word(Opcode.LDI, 2, -1, 0),
				new Word(Opcode.ADD, 2, 0, -1),
				new Word(Opcode.SUB, 2, 1, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.SUBI, 6, -1, 1),
				new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
				new Word(Opcode.STX, 5, 1, -1),
				new Word(Opcode.SUBI, 4, -1, 1),
				new Word(Opcode.STX, 4, 0, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
				new Word(Opcode.ADDI, 5, -1, 1),
				new Word(Opcode.SUBI, 7, -1, 1),
				new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
				new Word(Opcode.ADD, 4, 5, -1),
				new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
				new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
				new Word(Opcode.STOP, -1, -1, -1), // POS 45
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };

	};
}

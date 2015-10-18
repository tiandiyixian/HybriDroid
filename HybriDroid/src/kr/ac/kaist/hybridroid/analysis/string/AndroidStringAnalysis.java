package kr.ac.kaist.hybridroid.analysis.string;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import kr.ac.kaist.hybridroid.analysis.string.constraint.Box;
import kr.ac.kaist.hybridroid.analysis.string.constraint.BoxVisitor;
import kr.ac.kaist.hybridroid.analysis.string.constraint.ConstraintGraph;
import kr.ac.kaist.hybridroid.analysis.string.constraint.ConstraintVisitor;
import kr.ac.kaist.hybridroid.analysis.string.constraint.VarBox;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.dalvik.classLoader.DexFileModule;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.dalvik.ipa.callgraph.impl.AndroidEntryPoint;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator.LocatorFlags;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyClassTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyMethodTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFABuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;

/**
 * 
 * @author Sungho Lee
 */
public class AndroidStringAnalysis implements StringAnalysis{
	private AnalysisScope scope;
	private WorkList worklist;
	
	public AndroidStringAnalysis(){
		scopeInit();
		worklist = new WorkList();
	}
	
	private void scopeInit(){
		scope = AnalysisScope.createJavaAnalysisScope();
		scope.setLoaderImpl(ClassLoaderReference.Primordial, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
		scope.setLoaderImpl(ClassLoaderReference.Application, "com.ibm.wala.dalvik.classLoader.WDexClassLoaderImpl");
	}

	public void setExclusion(String exclusions){
		File exclusionsFile = new File(exclusions);
		try {
			InputStream fs = exclusionsFile.exists() ? new FileInputStream(exclusionsFile) : FileProvider.class.getClassLoader()
			    .getResourceAsStream(exclusionsFile.getName());
			scope.setExclusions(new FileOfClasses(fs));
			fs.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void addAnalysisScope(String path){
		// TODO Auto-generated method stub
		if(path.endsWith(".apk")){
			try {
				scope.addToScope(ClassLoaderReference.Application, DexFileModule.make(new File(path)));
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			throw new InternalError("Support only apk format as target file");	
		}
	}

	public void setupAndroidLibs(String... libs){
		try{
			for(String lib : libs){
				if(lib.endsWith(".dex"))
					scope.addToScope(ClassLoaderReference.Primordial, DexFileModule.make(new File(lib)));
				else if(lib.endsWith(".jar"))
					scope.addToScope(ClassLoaderReference.Primordial, new JarFileModule(new JarFile(new File(lib))));
			}
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	@Override
	public void analyze(List<Hotspot> hotspots) throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException {
		// TODO Auto-generated method stub
		CallGraph cg = buildCG();
//		WalaCGVisualizer vis = new WalaCGVisualizer();
//		vis.visualize(cg, "cfg_web.dot");
		Set<Box> boxSet = findHotspots(cg, hotspots);
		Box[] boxes = boxSet.toArray(new Box[0]);
		for(Box box : boxes){
			System.err.println("Spot: " + box);
		}
		
		buildConstraintGraph(cg, boxes);
	}
	
	private CallGraph buildCG() throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException{
		IClassHierarchy cha = ClassHierarchy.make(scope);
		AnalysisOptions options = new AnalysisOptions();
		IRFactory<IMethod> irFactory = new DexIRFactory();
		AnalysisCache cache = new AnalysisCache(irFactory);
		options.setReflectionOptions(ReflectionOptions.NONE);
		options.setAnalysisScope(scope);
		options.setEntrypoints(getEntrypoints(cha, scope, options, cache));
		options.setSelector(new ClassHierarchyClassTargetSelector(cha));
		options.setSelector(new ClassHierarchyMethodTargetSelector(cha));
		CallGraphBuilder cgb = new nCFABuilder(1, cha, options, cache, null, null);
		return cgb.makeCallGraph(options, null);
	}
	
	private Iterable<Entrypoint> getEntrypoints(final IClassHierarchy cha, AnalysisScope scope, AnalysisOptions option, AnalysisCache cache){
		Iterable<Entrypoint> entrypoints = null;
		
		if(cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial, "Lgeneratedharness/GeneratedAndroidHarness")) == null){
			Set<LocatorFlags> flags = HashSetFactory.make();
			flags.add(LocatorFlags.INCLUDE_CALLBACKS);
			flags.add(LocatorFlags.EP_HEURISTIC);
			flags.add(LocatorFlags.CB_HEURISTIC);
			AndroidEntryPointLocator eps = new AndroidEntryPointLocator(flags);
			List<AndroidEntryPoint> es = eps.getEntryPoints(cha);
					
			final List<Entrypoint> entries = new ArrayList<Entrypoint>();
			for (AndroidEntryPoint e : es) {
				entries.add(e);
			}
	
			entrypoints = new Iterable<Entrypoint>() {
				@Override
				public Iterator<Entrypoint> iterator() {
					return entries.iterator();
				}
			};
		}else{
			IClass root = cha.lookupClass(TypeReference.find(ClassLoaderReference.Primordial, "Lgeneratedharness/GeneratedAndroidHarness"));
			IMethod rootMethod = root.getMethod(new Selector(Atom.findOrCreateAsciiAtom("androidMain"), Descriptor.findOrCreate(null, TypeName.findOrCreate("V"))));
			Entrypoint droidelEntryPoint = new DefaultEntrypoint(rootMethod, cha);
			
			final List<Entrypoint> entry = new ArrayList<Entrypoint>();
			entry.add(droidelEntryPoint);
			
			entrypoints = new Iterable<Entrypoint>(){
				@Override
				public Iterator<Entrypoint> iterator(){
					return entry.iterator();
				}
			};
		}
		return entrypoints;
	}
	
	private Set<Box> findHotspots(CallGraph cg, List<Hotspot> hotspots){
		Set<Box> boxes = new HashSet<Box>();
		for(CGNode node : cg){
			IR ir = node.getIR();
			
			if(ir == null)
				continue;
			
			SSAInstruction[] insts = ir.getInstructions();
			for(int i=0; i<insts.length; i++){
				SSAInstruction inst = insts[i];
				
				if(inst == null)
					continue;
				
				for(Hotspot hotspot : hotspots){
					if(isHotspot(inst, hotspot)){
						int use = inst.getUse(hotspot.index() + 1);
						boxes.add(new VarBox(node, i, use));
					}
				}
			}
		}
		return boxes;
	}
	
	private boolean isHotspot(SSAInstruction inst, Hotspot hotspot){
		if(hotspot instanceof ArgumentHotspot){
			ArgumentHotspot argHotspot = (ArgumentHotspot) hotspot;
			if(inst instanceof SSAAbstractInvokeInstruction){
				SSAAbstractInvokeInstruction invokeInst = (SSAAbstractInvokeInstruction) inst;
				MethodReference targetMr = invokeInst.getDeclaredTarget();
				if(targetMr.getName().toString().equals(argHotspot.getMethodName()) && targetMr.getNumberOfParameters() == argHotspot.getParamNum())
					return true;
			}
		}
		return false;
	}
	
	private void buildConstraintGraph(CallGraph cg, Box... initials){
		ConstraintGraph graph = new ConstraintGraph();
		BoxVisitor<Set<Box>> v = new ConstraintVisitor(cg, graph);
		for(Box initial : initials)
			worklist.add(initial);
		
		int iter = 1;
		while(!worklist.isEmpty()){
			Box box = worklist.pop();
			System.out.println("#Iter(" + iter + ") " + box);
			Set<Box> res = box.visit(v);
			
			for(Box next : res)
				worklist.add(next);
		}
	}

	class WorkList{
		private List<Box> list;
		
		public WorkList(){
			list = new ArrayList<Box>();
		}
		
		public void add(Box box){
			list.add(box);
		}
		
		public Box pop(){
			Box box = list.get(0);
			list.remove(0);
			return box;
		}
		
		public boolean isEmpty(){
			return list.isEmpty();
		}
	}
}
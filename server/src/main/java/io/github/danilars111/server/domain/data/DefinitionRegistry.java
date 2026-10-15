package io.github.danilars111.server.domain.data;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import io.github.danilars111.contracts.definition.annotations.DefinitionPath;
import io.github.danilars111.contracts.definition.interfaces.Definition;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefinitionRegistry {

    private static final Logger LOGGER = System.getLogger(DefinitionRegistry.class.getName());
    private final Map<Class<? extends Definition>, DefinitionRepository<?>> repositories = new ConcurrentHashMap<>();
    private final Map<Class<? extends Definition>, String> subfolderMappings = new ConcurrentHashMap<>();
    private final Map<Class<? extends Definition>, List<Runnable>> reloadListeners = new ConcurrentHashMap<>();

    private final DataLoader loader;
    private final String scanPackage;

    public DefinitionRegistry(DataLoader loader, String scanPackage) {
        this.loader = loader;
        this.scanPackage = scanPackage;
    }

    @SuppressWarnings("unchecked")
    public void discoverAndLoadAll() throws IOException {
        try (ScanResult scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .acceptPackages(scanPackage)
                .scan()) {

            ClassInfoList classInfos = scanResult.getClassesWithAnnotation(DefinitionPath.class.getName());
            for (ClassInfo classInfo : classInfos) {
                Class<?> clazz = classInfo.loadClass();
                if (Definition.class.isAssignableFrom(clazz)) {
                    Class<? extends Definition> defClass = (Class<? extends Definition>) clazz;
                    DefinitionPath pathAnnotation = defClass.getAnnotation(DefinitionPath.class);
                    String subfolder = pathAnnotation.value();

                    subfolderMappings.put(defClass, subfolder);
                    DefinitionRepository<?> repository = repositories.computeIfAbsent(defClass, k -> new DefinitionRepository<>());

                    dispatchLoad(repository, subfolder, defClass);

                    LOGGER.log(Level.INFO, "Discovered and loaded [{0}] from path: {1}", defClass.getSimpleName(), subfolder);
                }

            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Definition> void dispatchLoad(
            DefinitionRepository<?> repository,
            String subfolder,
            Class<T> type
    ) throws IOException {
        DefinitionRepository<T> typedRepo = (DefinitionRepository<T>) repository;
        loader.reloadDefinitions(typedRepo, subfolder, type);
    }

    @SuppressWarnings("unchecked")
    public <T extends Definition> DefinitionRepository<T> getRepository(Class<T> type) {
        DefinitionRepository<T> typedRepo = (DefinitionRepository<T>) repositories.get(type);

        if (typedRepo == null) {
            throw new IllegalStateException("No definition registered for type " + type);
        }
        return typedRepo;
    }

    @SuppressWarnings("unchecked")
    public void reloadAll() {
        for (var entry : subfolderMappings.entrySet()) {
            Class<? extends Definition> type = entry.getKey();
            String subfolder = entry.getValue();
            DefinitionRepository<?> repo = repositories.get(type);

            if (repo != null) {
                try {
                    dispatchLoad(repo, subfolder, type);
                    List<Runnable> listeners = reloadListeners.get(type);
                    if (listeners != null) {
                        listeners.forEach(Runnable::run);
                    }
                    LOGGER.log(Level.INFO, "Reloaded definitions for: {0}", type.getSimpleName());
                } catch (IOException e) {
                    LOGGER.log(Level.ERROR, "Failed to reload definitions for: " + type.getSimpleName(), e);
                }
            }
        }
    }
    public <T extends Definition> void onReload(Class<T> type, Runnable listener) {
        reloadListeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

}

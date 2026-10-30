import SwiftUI
import UIKit
import SharedUi

// DIAGNÓSTICO TEMPORAL (T2 de odd/tasks/ios-real-ui.md, build 244).
//
// Por qué existe: el simulador dibuja el marcador en Debug y en Release, y el iPhone 12 mini muestra negro
// con el proceso vivo. No hay forma de ver esa pantalla ni su os_log desde CI, así que el arranque se
// instrumenta para que la respuesta salga por stdout (que `devicectl --console` captura) y por los ojos del
// humano. Se retira cuando T2 cierre: no es parte de la app.
//
// No cambia comportamiento: la composición de marcador sigue siendo la misma y lo único que se añade es un
// cuadrado rojo de UIKit por encima y unas líneas impresas con el prefijo RPDIAG:.

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        RpDiagnosticHostController(compose: MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

/// Contenedor con dos cosas: el controlador de Compose como hijo y un cuadrado rojo de UIKit encima.
/// El cuadrado es un `UIView` de verdad, no una forma de SwiftUI: si el humano lo ve, UIKit pinta y el que
/// no dibuja es Compose; si no ve nada, ni siquiera estamos mirando nuestra ventana.
final class RpDiagnosticHostController: UIViewController {
    private let compose: UIViewController
    private let calibre = UIView(frame: CGRect(x: 0, y: 0, width: 80, height: 80))

    init(compose: UIViewController) {
        self.compose = compose
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) no se usa en este contenedor")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)

        // Hermano posterior: queda por encima de la vista de Compose.
        calibre.backgroundColor = .systemRed
        calibre.accessibilityIdentifier = "RPDIAG-calibre"
        view.addSubview(calibre)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // Un turno del runloop después: Compose ya tuvo su primera oportunidad de dibujar un frame.
        DispatchQueue.main.async { [weak self] in
            self?.imprimirDiagnostico()
        }
    }

    private func imprimirDiagnostico() {
        let pantalla = UIScreen.main
        print("RPDIAG: screen.bounds=\(pantalla.bounds) scale=\(pantalla.scale) nativeScale=\(pantalla.nativeScale)")

        let escena = view.window?.windowScene
        print("RPDIAG: scene.activationState=\(escena.map { "\($0.activationState.rawValue)" } ?? "sin ventana")")

        if let ventana = view.window {
            print("RPDIAG: window.bounds=\(ventana.bounds) frame=\(ventana.frame) isHidden=\(ventana.isHidden) alpha=\(ventana.alpha)")
            print("RPDIAG: window.safeAreaInsets=\(ventana.safeAreaInsets)")
        } else {
            print("RPDIAG: window=nil")
        }

        let anfitrion = view!
        print("RPDIAG: host.frame=\(anfitrion.frame) host.bounds=\(anfitrion.bounds) safeAreaInsets=\(anfitrion.safeAreaInsets)")

        let vistaCompose = compose.view!
        print("RPDIAG: compose.frame=\(vistaCompose.frame) bounds=\(vistaCompose.bounds)")
        print("RPDIAG: compose.isHidden=\(vistaCompose.isHidden) alpha=\(vistaCompose.alpha) enVentana=\(vistaCompose.window != nil) superview=\(vistaCompose.superview.map { String(describing: type(of: $0)) } ?? "nil")")

        let capas = metalLayers(en: vistaCompose.layer)
        print("RPDIAG: compose.layer.sublayers=\(vistaCompose.layer.sublayers?.map { String(describing: type(of: $0)) } ?? [])")
        print("RPDIAG: metalLayers=\(capas.count)")
        for (indice, capa) in capas.enumerated() {
            print("RPDIAG: metal[\(indice)] contentsScale=\(capa.contentsScale) drawableSize=\(capa.drawableSize) bounds=\(capa.bounds) isHidden=\(capa.isHidden) opacity=\(capa.opacity) device=\(capa.device.map { $0.name } ?? "nil")")
        }

        print("RPDIAG: calibre.frame=\(calibre.frame) calibre.window=\(calibre.window != nil) calibre.superview=\(calibre.superview.map { String(describing: type(of: $0)) } ?? "nil")")
    }

    private func metalLayers(en capa: CALayer) -> [CAMetalLayer] {
        var encontradas: [CAMetalLayer] = []
        if let metal = capa as? CAMetalLayer {
            encontradas.append(metal)
        }
        for subcapa in capa.sublayers ?? [] {
            encontradas.append(contentsOf: metalLayers(en: subcapa))
        }
        return encontradas
    }
}

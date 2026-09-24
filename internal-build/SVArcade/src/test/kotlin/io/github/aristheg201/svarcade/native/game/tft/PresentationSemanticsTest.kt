package io.github.aristheg201.svarcade.native.game.tft
import kotlin.test.*
class PresentationSemanticsTest {
 @Test fun allRequiredSemanticsResolveProviderLabelsOrExplicitPoserDefault(){assertEquals(13,PokemonAnimationSemantic.entries.size);PokemonAnimationSemantic.entries.forEach{val r=PokemonAnimationResolver.resolve(it,listOf("idle","walk","physical","special","cast","run","hit","recoil","faint","transform","send_out","victory","cry"));assertNotNull(r.selectedLabel,"$it")}}
 @Test fun greenLanternMegaIdentityNeverDropsEitherResolverAspect(){for(form in listOf("mega-x","mega-y")){val p=PokemonPresentationIdentity("cobblemon:mewtwo",form,setOf("greenlantern"));assertEquals(setOf("greenlantern",form),p.resolverAspects())}}
}

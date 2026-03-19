package br.com.edras.picpaysimplificado.fixtures;

import br.com.edras.picpaysimplificado.entity.CommonUser;

public class CommonUserFixtures {

    public static CommonUser createValidCommonUser() {
        CommonUser user = new CommonUser();
        user.setName("João");
        user.setEmail("joao@email.com");
        user.setPassword("senha123");
        user.setCpf("84564864009");
        return user;
    }

    public static CommonUser createValidCommonUser(Long id) {
        CommonUser user = createValidCommonUser();
        user.setId(id);
        return user;
    }

}

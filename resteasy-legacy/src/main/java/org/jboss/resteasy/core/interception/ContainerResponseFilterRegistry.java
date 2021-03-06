package org.jboss.resteasy.core.interception;

import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.core.interception.jaxrs.ContainerResponseContextImpl;
import org.jboss.resteasy.specimpl.BuiltResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.AcceptedByMethod;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 * @deprecated Use org.jboss.resteasy.core.interception.jaxrs.ContainerResponseFilterRegistry instead.
 */
@Deprecated
public class ContainerResponseFilterRegistry extends org.jboss.resteasy.core.interception.jaxrs.ContainerResponseFilterRegistry
{
   protected LegacyPrecedence precedence;

   public ContainerResponseFilterRegistry(ResteasyProviderFactory providerFactory)
   {
      this(providerFactory, new LegacyPrecedence());
   }

   public ContainerResponseFilterRegistry(ResteasyProviderFactory providerFactory, LegacyPrecedence precedence)
   {
      super(providerFactory);
      this.precedence = precedence;
   }

   private static class ContainerResponseFilterFacade implements ContainerResponseFilter
   {
      protected final PostProcessInterceptor interceptor;

      private ContainerResponseFilterFacade(PostProcessInterceptor interceptor)
      {
         this.interceptor = interceptor;
      }

      @Override
      public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException
      {
         ContainerResponseContextImpl ctx = (ContainerResponseContextImpl)responseContext;
         BuiltResponse jaxrsResposne = ctx.getJaxrsResponse();
         ServerResponse serverResponse = new ServerResponse(jaxrsResposne);
         try
         {
            interceptor.postProcess(serverResponse);
         }
         finally
         {
            jaxrsResposne.setStatus(serverResponse.getStatus());
            jaxrsResposne.setAnnotations(serverResponse.getAnnotations());
            jaxrsResposne.setEntity(serverResponse.getEntity());
            jaxrsResposne.setMetadata(serverResponse.getMetadata());
            jaxrsResposne.setEntityClass(serverResponse.getEntityClass());
            jaxrsResposne.setGenericType(serverResponse.getGenericType());
         }

      }
   }

   public ContainerResponseFilterRegistry clone(ResteasyProviderFactory factory)
   {
      ContainerResponseFilterRegistry clone = new ContainerResponseFilterRegistry(factory, precedence);
      clone.interceptors.addAll(interceptors);
      return clone;
   }
   
   public abstract class AbstractLegacyInterceptorFactory extends AbstractInterceptorFactory
   {
      protected LegacyPrecedence precedence;

      protected AbstractLegacyInterceptorFactory(Class declaring, LegacyPrecedence precedence)
      {
         super(declaring);
         this.precedence = precedence;
      }

      @Override
      protected void setPrecedence(Class<?> declaring)
      {
         order = precedence.calculateOrder(declaring);
      }

      @Override
      public Match preMatch()
      {
         return null;
      }

      public Object getLegacyMatch(Class declaring, AccessibleObject target)
      {
         Object interceptor = getInterceptor();
         if (interceptor instanceof AcceptedByMethod)
         {
            if (target == null || !(target instanceof Method)) return null;
            Method method = (Method) target;
            if (((AcceptedByMethod) interceptor).accept(declaring, method))
            {
               return interceptor;
            } else
            {
               return null;
            }
         }
         return interceptor;
      }

   }

   protected class LegacySingletonInterceptorFactory extends AbstractLegacyInterceptorFactory
   {
      protected Object interceptor;

      public LegacySingletonInterceptorFactory(Class declaring, Object interceptor, LegacyPrecedence precedence)
      {
         super(declaring, precedence);
         this.interceptor = interceptor;
         setPrecedence(declaring);
      }

      @Override
      protected void initialize()
      {
         providerFactory.injectProperties(interceptor);
      }

      @Override
      protected Object getInterceptor()
      {
         checkInitialize();
         return interceptor;
      }
   }

   protected class LegacyPerMethodInterceptorFactory extends AbstractLegacyInterceptorFactory
   {

      public LegacyPerMethodInterceptorFactory(Class declaring, LegacyPrecedence precedence)
      {
         super(declaring, precedence);
         setPrecedence(declaring);
      }

      @Override
      protected void initialize()
      {
      }

      @Override
      protected Object getInterceptor()
      {
         Object interceptor = createInterceptor();
         providerFactory.injectProperties(interceptor);
         return interceptor;
      }
   }

   public void registerLegacy(Class<? extends PostProcessInterceptor> decl)
   {
      register(new LegacyPerMethodInterceptorFactory(decl, precedence)
      {
         @Override
         public Match postMatch(Class declaring, AccessibleObject target)
         {
            Object obj = getLegacyMatch(declaring, target);
            if (obj == null) return null;
            PostProcessInterceptor interceptor = (PostProcessInterceptor)obj;
            return new Match(new ContainerResponseFilterFacade(interceptor), order);
         }

      });
   }

   public void registerLegacy(PostProcessInterceptor interceptor)
   {
      register(new LegacySingletonInterceptorFactory(interceptor.getClass(), interceptor, precedence)
      {
         @Override
         public Match postMatch(Class declaring, AccessibleObject target)
         {
            Object obj = getLegacyMatch(declaring, target);
            if (obj == null) return null;
            PostProcessInterceptor interceptor = (PostProcessInterceptor)obj;
            return new Match(new ContainerResponseFilterFacade(interceptor), order);
         }
      });

   }
}
